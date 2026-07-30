# 서버 RCON 클라이언트 — 콘솔 창에 손대지 않고 명령을 보낸다.
#
# 비밀번호는 server/server.properties 에서 읽는다(그 파일은 .gitignore 대상).
# 절대 출력하지 않으며, 인자로도 받지 않는다 — 인자로 받으면 셸 히스토리에 남는다.
#
# 사용:
#   powershell -File tools\rcon.ps1 -Command "siege status"
#   powershell -File tools\rcon.ps1 -Command stop
#   powershell -File tools\rcon.ps1 -Command stop -AllowStopWithPlayers
#
# ── 알아둘 함정 둘 ──
# 1. MC 의 RconClient 는 «패킷 하나가 read() 한 번에 다 들어오는 것»을 전제한다
#    (길이 필드가 읽은 바이트-4 와 정확히 같아야 한다). 그래서 헤더만 먼저 읽고
#    본문을 나눠 읽는 통상적인 구현은 여기서 어긋난다 — 통째로 한 번에 읽는다.
# 2. 인증 직후 첫 명령의 응답이 간헐적으로 안 온다(원인 미상, 재현율 절반쯤).
#    그래서 응답 없으면 재시도한다. 재시도까지 실패하면 «모른다»로 취급한다 —
#    stop 은 그 상태에서 보내지 않는다. 접속자 확인이 실패했는데 서버를 내리면
#    사람을 끊는 것이므로, 이 가드는 반드시 fail-closed 여야 한다.
param(
    [Parameter(Mandatory = $true)][string]$Command,
    [switch]$AllowStopWithPlayers
)

$ErrorActionPreference = 'Stop'
$OutputEncoding = [Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$props = Join-Path $PSScriptRoot '..\server\server.properties'
if (-not (Test-Path $props)) { Write-Output "ERR: server.properties 없음: $props"; exit 1 }
# 한 번 파싱해서 표로 만든 뒤 꺼내 쓴다. 키마다 match/replace 를 반복하던 것을 접었는데,
# 부수 효과가 하나 더 있다 — 예전 형태는 시크릿 스캐너(pre-push f4a)가 «password= 뒤에
# 따옴표»를 하드코딩된 자격증명으로 오탐해서 push 마다 걸렸다. 상시 울리는 경보는
# 무시하게 되므로 규칙을 끄는 대신 코드를 바꿨다.
$raw = Get-Content $props -Encoding UTF8
$conf = @{}
foreach ($line in $raw) {
    if ($line -match '^\s*([^#=]+?)\s*=\s*(.*)$') { $conf[$Matches[1]] = $Matches[2].Trim() }
}
$pw = $conf['rcon.password']
$port = $conf['rcon.port']
$enabled = $conf['enable-rcon']
if ($enabled -ne 'true') { Write-Output 'ERR: enable-rcon=false'; exit 1 }
if (-not $pw) { Write-Output 'ERR: rcon.password 비어 있음'; exit 1 }

$c = New-Object System.Net.Sockets.TcpClient
try { $c.Connect('127.0.0.1', [int]$port) }
catch { Write-Output "ERR: 접속 실패 (서버가 안 떠 있나?) — $($_.Exception.Message)"; exit 1 }
$ns = $c.GetStream(); $ns.ReadTimeout = 10000

function Send-Rcon($id, $type, $body) {
    $b = [System.Text.Encoding]::UTF8.GetBytes($body)
    $ms = New-Object System.IO.MemoryStream
    $bw = New-Object System.IO.BinaryWriter($ms)
    $bw.Write([int](10 + $b.Length))   # 길이 = id(4) + type(4) + 본문 + 널 2
    $bw.Write([int]$id); $bw.Write([int]$type)
    $bw.Write($b); $bw.Write([byte]0); $bw.Write([byte]0); $bw.Flush()
    $a = $ms.ToArray(); $ns.Write($a, 0, $a.Length); $ns.Flush()
}

# 응답 없으면 $null. 예외를 밖으로 던지지 않는다 — 호출부가 «모른다»를 구분해야 하므로.
function Receive-Rcon {
    try {
        $buf = New-Object byte[] 8192
        $n = $ns.Read($buf, 0, 8192)
        if ($n -le 0) { return $null }
        return @{
            Id   = [BitConverter]::ToInt32($buf, 4)
            Body = [System.Text.Encoding]::UTF8.GetString($buf, 12, [Math]::Max(0, $n - 14))
        }
    } catch { return $null }
}

# 같은 명령을 다시 보내 응답을 한 번 더 기다린다(함정 2).
function Invoke-Rcon($id, $body) {
    Send-Rcon $id 2 $body
    $r = Receive-Rcon
    if ($null -ne $r) { return $r }
    Send-Rcon ($id + 100) 2 $body
    return Receive-Rcon
}

Send-Rcon 1 3 $pw
$auth = Receive-Rcon
if ($null -eq $auth -or $auth.Id -eq -1) { Write-Output 'ERR: RCON 인증 실패'; $c.Close(); exit 1 }

# stop 은 접속자를 «확인했을 때만» 보낸다. 확인 자체가 실패하면 보내지 않는다.
if ($Command -eq 'stop' -and -not $AllowStopWithPlayers) {
    $listRes = Invoke-Rcon 2 'list'
    if ($null -eq $listRes) {
        Write-Output 'ABORT: list 응답 없음 — 접속자를 확인 못 했으므로 stop 을 보내지 않는다.'
        Write-Output '       (확인했고 그래도 내릴 거면 -AllowStopWithPlayers)'
        $c.Close(); exit 2
    }
    Write-Output "list> $($listRes.Body)"
    $online = -1
    if ($listRes.Body -match 'There are (\d+)') { $online = [int]$Matches[1] }
    if ($online -lt 0) {
        Write-Output "ABORT: list 응답을 해석 못 했다 — stop 을 보내지 않는다."
        $c.Close(); exit 2
    }
    if ($online -gt 0) {
        Write-Output "ABORT: 접속자 $online 명 — stop 을 보내지 않는다. (-AllowStopWithPlayers 로 강제)"
        $c.Close(); exit 2
    }
}

$res = Invoke-Rcon 3 $Command
if ($null -eq $res) { Write-Output "$Command> (응답 없음 — stop 이면 정상, 아니면 실패를 의심할 것)" }
else { Write-Output "$Command> $($res.Body)" }
$c.Close()
