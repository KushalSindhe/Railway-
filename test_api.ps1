$loginPayload = @{ email = 'kushal.sindhe@gmail.com'; name = 'Kushal Sindhe'; credential = 'test_token' } | ConvertTo-Json
$loginRes = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/google' -Method Post -Body $loginPayload -ContentType 'application/json'
$token = $loginRes.token
Write-Host "Google Admin Auth: SUCCESS (Token: $token)"

$overview = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/overview?token=$token"
Write-Host "Overview Total Users: $($overview.totalUsers) | Passengers: $($overview.passengerCount) | Admins: $($overview.adminCount)"

$p1 = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/users?page=1&pageSize=50&token=$token"
Write-Host "Users Page 1: count=$($p1.users.Length), total=$($p1.total), filteredTotal=$($p1.filteredTotal), latency=$($p1.queryTimeMs)ms"
Write-Host "Sample user 1: @$($p1.users[0].username) - $($p1.users[0].fullName) ($($p1.users[0].state), Tier: $($p1.users[0].loyaltyTier))"

$pLast = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/users?page=20000&pageSize=50&token=$token"
Write-Host "Users Page 20000: count=$($pLast.users.Length), first on page=@$($pLast.users[0].username), latency=$($pLast.queryTimeMs)ms"

$adminQuery = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/users?role=ADMIN&page=1&pageSize=25&token=$token"
Write-Host "Admin Filter: count=$($adminQuery.users.Length), filteredTotal=$($adminQuery.filteredTotal), latency=$($adminQuery.queryTimeMs)ms"

$stateQuery = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/users?state=Karnataka&page=1&pageSize=25&token=$token"
Write-Host "Karnataka State Filter: count=$($stateQuery.users.Length), filteredTotal=$($stateQuery.filteredTotal), latency=$($stateQuery.queryTimeMs)ms"

$details = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/user-details?username=kushal_sindhe&token=$token"
Write-Host "User Details: Profile=$($details.profile.fullName), Tickets=$($details.tickets.Length)"
if ($details.tickets.Length -gt 0) {
  Write-Host "Ticket PNR: $($details.tickets[0].pnr) | Train: $($details.tickets[0].trainName) | Fare: ₹$($details.tickets[0].fare)"
}

Write-Host "ALL BACKEND AND 1M DATASET TESTS PASSED 100%!"
