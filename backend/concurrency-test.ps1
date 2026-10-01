
param(
    [Parameter(Mandatory=$true)][string]$AccessToken,
    [int]$RoomId = 1
)

$ErrorActionPreference = "Stop"

$uri = "http://localhost:8080/api/bookings"
$headers = @{ Authorization = "Bearer $AccessToken"; "Content-Type" = "application/json" }
$checkIn = [DateTimeOffset]::UtcNow.AddDays(1).ToUnixTimeMilliseconds()
$checkOut = [DateTimeOffset]::UtcNow.AddDays(2).ToUnixTimeMilliseconds()
$body = @{
    roomId = $RoomId
    quantity = 1
    checkInDate = $checkIn
    checkOutDate = $checkOut
    guests = 1
} | ConvertTo-Json

Write-Host "Before running, set room $RoomId available_rooms = 1 in MySQL."
Write-Host "Sending two booking requests concurrently..."

$jobs = 1..2 | ForEach-Object {
    Start-Job -ScriptBlock {
        param($uri, $headers, $body, $number)
        try {
            $response = Invoke-WebRequest -Uri $uri -Method Post -Headers $headers -Body $body -UseBasicParsing
            [PSCustomObject]@{ Request=$number; Status=[int]$response.StatusCode; Body=$response.Content }
        } catch {
            $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
            [PSCustomObject]@{ Request=$number; Status=$status; Body=$_.ErrorDetails.Message }
        }
    } -ArgumentList $uri, $headers, $body, $_
}

$jobs | Wait-Job | Receive-Job | Format-Table -AutoSize
$jobs | Remove-Job
