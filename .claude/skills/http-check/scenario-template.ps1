# Modele de scenario HTTP Billet+ - PowerShell 5.1.
# Execution : powershell -NoProfile -ExecutionPolicy Bypass -File .\scenario.ps1 [-Port 8081]
#
# Garder : controle d'identite + Send-Request (code ET corps, meme sur 4xx/5xx).
# Remplacer : la section "Scenario" en bas.
param([int]$Port = 8081)

$base = "http://localhost:$Port"

# Invoke-WebRequest leve une exception sur 4xx/5xx : on lit le code et le corps dans le catch.
function Send-Request($method, $uri, $body = $null, $headers = @{}) {
  try {
    $r = Invoke-WebRequest -Method $method -Uri $uri -Headers $headers -Body $body `
         -ContentType "application/json" -UseBasicParsing
    return [pscustomobject]@{ Status = [int]$r.StatusCode; Body = $r.Content }
  } catch {
    $resp = $_.Exception.Response
    if ($null -eq $resp) { return [pscustomobject]@{ Status = 0; Body = $_.Exception.Message } }
    $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
    return [pscustomobject]@{ Status = [int]$resp.StatusCode; Body = $reader.ReadToEnd() }
  }
}

# Cree un compte (toujours ORGANISATEUR) puis renvoie son access token (valable 15 minutes).
function New-Account($email, $nom) {
  $reg = Send-Request POST "$base/api/auth/register" (@{ email = $email; motDePasse = "MotDePasse123"; nomComplet = $nom } | ConvertTo-Json)
  if ($reg.Status -ne 201) { throw "register a repondu $($reg.Status) : $($reg.Body)" }
  $login = Send-Request POST "$base/api/auth/login" (@{ email = $email; motDePasse = "MotDePasse123" } | ConvertTo-Json)
  if ($login.Status -ne 200) { throw "login a repondu $($login.Status) : $($login.Body)" }
  return ($login.Body | ConvertFrom-Json).accessToken
}

function Show-Result($etape, $attendu, $res) {
  $ok = if ($res.Status -eq $attendu) { "OK  " } else { "ECART" }
  $corps = if ($res.Body) { $res.Body.Substring(0, [Math]::Min(200, $res.Body.Length)) } else { "(vide)" }
  "{0} {1,-34} attendu {2} / obtenu {3}  {4}" -f $ok, $etape, $attendu, $res.Status, $corps
}

function Test-Scenario {
  # 0. Controle d'identite : seul Billet+ repond "Lieu introuvable" sur cette route.
  $ident = Send-Request GET "$base/api/venues/00000000-0000-0000-0000-000000000000"
  if ($ident.Status -ne 404 -or $ident.Body -notmatch "Lieu introuvable") {
    "STOP : ce serveur n'est pas Billet+ (statut $($ident.Status))."
    "Demarre Billet+ sur le port $Port dans l'autre terminal, puis relance le scenario."
    return
  }
  "identite du serveur : Billet+ OK (port $Port)"

  # --- Scenario (a remplacer) -------------------------------------------------
  $s = Get-Random
  $token = New-Account "scenario$s@test.local" "Scenario Un"
  $auth  = @{ Authorization = "Bearer $token" }

  Show-Result "GET /api/auth/me (avec jeton)"  200 (Send-Request GET "$base/api/auth/me" $null $auth)
  Show-Result "GET /api/auth/me (sans jeton)"  401 (Send-Request GET "$base/api/auth/me")
  Show-Result "GET /api/events (pas de liste)" 405 (Send-Request GET "$base/api/events")
  # ----------------------------------------------------------------------------
}

Test-Scenario
