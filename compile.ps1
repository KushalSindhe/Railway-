$currDir = (Get-Location).Path.Replace('\', '/') + '/'
$sources = Get-ChildItem -Path "src" -Filter "*.java" -Recurse | ForEach-Object {
    $p = $_.FullName.Replace('\', '/')
    $p.Replace($currDir, '')
}
$sources | Set-Content "sources.txt"
& javac -d bin "@sources.txt"
if ($LASTEXITCODE -eq 0) {
    Write-Host "Java compilation succeeded!"
    Remove-Item "sources.txt" -ErrorAction SilentlyContinue
} else {
    Write-Host "Java compilation failed with code $LASTEXITCODE"
}
