#requires -Version 5.1

$ErrorActionPreference = 'Stop'

$apiUrl = 'https://6a07e24cfa9b27c848fa5e20.mockapi.io/erpGenerico/products'.TrimEnd('/')
$listUrl = $apiUrl + '?page=1&limit=100'
$expectedCount = 100

$products = @(
    @{ sku='SW-BOV-001'; name='Picanha Combo Swift'; category='Bovinos'; price=69.90; cost=52.00; unit='Kg' },
    @{ sku='SW-BOV-002'; name='Medalhão de Picanha Legado 1855'; category='Bovinos'; price=39.90; cost=29.00; unit='Embalagem' },
    @{ sku='SW-BOV-003'; name='Hambúrguer Sabor Picanha Swift 480g'; category='Bovinos'; price=19.90; cost=13.00; unit='Embalagem' },
    @{ sku='SW-AVE-001'; name='Filé de Peito de Frango Swift 1Kg'; category='Aves'; price=18.90; cost=13.00; unit='Embalagem' },
    @{ sku='SW-AVE-002'; name='Filezinho Sassami Swift 1Kg'; category='Aves'; price=18.90; cost=13.00; unit='Embalagem' },
    @{ sku='SW-AVE-003'; name='Carne Moída de Frango Swift 500g'; category='Aves'; price=12.90; cost=8.50; unit='Embalagem' },
    @{ sku='SW-AVE-004'; name='Iscas de Frango Swift 300g'; category='Aves'; price=11.90; cost=7.50; unit='Embalagem' },
    @{ sku='SW-SUI-001'; name='Costelinha Suína a Passarinho Temperada Swift 1Kg'; category='Suínos'; price=23.90; cost=17.00; unit='Embalagem' },
    @{ sku='SW-SUI-002'; name='Espetinho de Panceta Temperada Swift 500g'; category='Suínos'; price=20.90; cost=14.00; unit='Embalagem' },
    @{ sku='SW-PES-001'; name='Hambúrguer de Salmão Swift 360g'; category='Pescados'; price=25.90; cost=18.00; unit='Embalagem' }
)

$branches = @(
    @{ code='SW-001'; name='Swift Campinas'; region='REG-SE' },
    @{ code='SW-002'; name='Swift São Paulo'; region='REG-SE' },
    @{ code='SW-003'; name='Swift Santos'; region='REG-SE' },
    @{ code='SW-004'; name='Swift Belo Horizonte'; region='REG-SE' },
    @{ code='SW-005'; name='Swift Vitória'; region='REG-SE' },
    @{ code='SW-006'; name='Swift Curitiba'; region='REG-S' },
    @{ code='SW-007'; name='Swift Porto Alegre'; region='REG-S' },
    @{ code='SW-008'; name='Swift Goiânia'; region='REG-CO' },
    @{ code='SW-009'; name='Swift Recife'; region='REG-NE' },
    @{ code='SW-010'; name='Swift Salvador'; region='REG-NE' }
)

Write-Host "Lendo os registros de $listUrl"
$listResponse = Invoke-RestMethod -Method Get -Uri $listUrl

if ($listResponse -is [System.Array]) {
    $rows = @($listResponse)
} elseif ($listResponse.PSObject.Properties.Name -contains 'value') {
    $rows = @($listResponse.value)
} else {
    throw 'Resposta inesperada do MockAPI: esperava uma lista ou um objeto com a propriedade value.'
}

if ($rows.Count -ne $expectedCount) {
    throw "O MockAPI retornou $($rows.Count) registros; esperava $expectedCount. Nada foi alterado."
}

$rows = @($rows | Sort-Object { [int]$_.id })

$ids = @($rows | ForEach-Object { [string]$_.id })
if ($ids -contains '') {
    throw 'Há registro sem id na resposta. Nada foi alterado.'
}
if (@($ids | Select-Object -Unique).Count -ne $expectedCount) {
    throw 'A resposta contém IDs repetidos. Nada foi alterado.'
}

$firstId = [uri]::EscapeDataString([string]$rows[0].id)
$firstItemUrl = "$apiUrl/$firstId"
Write-Host "Validando a rota individual: $firstItemUrl"
$firstItemResponse = Invoke-RestMethod -Method Get -Uri $firstItemUrl

if ($firstItemResponse.PSObject.Properties.Name -contains 'value') {
    $firstItem = @($firstItemResponse.value)[0]
} else {
    $firstItem = $firstItemResponse
}

if ([string]$firstItem.id -ne [string]$rows[0].id) {
    throw 'A rota individual não retornou o primeiro registro esperado. Nada foi alterado.'
}

Write-Host "Validação concluída. Preparando atualização de $expectedCount registros."
$updates = @()

for ($i = 0; $i -lt $expectedCount; $i++) {
    $productIndex = [int][Math]::Floor($i / $branches.Count)
    $product = $products[$productIndex]
    $branch = $branches[$i % $branches.Count]

    $expiry = [DateTime]::UtcNow.Date.AddDays(-10 + (($i * 7) % 300))
    $entry = $expiry.AddDays(-30 - ($i % 120))
    $sales7 = ($i * 13) % 70
    $sales30 = $sales7 + 20 + (($i * 19) % 160)

    $payload = @{
        codigo_produto_erp = $product.sku
        nome_produto = $product.name
        categoria = $product.category
        num_lote = ('LOTE-{0:D4}' -f ($i + 1))
        data_validade = $expiry.ToString("yyyy-MM-dd'T'HH:mm:ss'Z'")
        quantidade = 5 + (($i * 17) % 146)
        preco = $product.price
        custo = $product.cost
        codigo_filial_erp = $branch.code
        filial = $branch.name
        region_id = $branch.region
        certificado_qualidade = ($i % 4 -ne 0)
        data_entrada = $entry.ToString("yyyy-MM-dd'T'HH:mm:ss'Z'")
        vendas_7d = $sales7
        vendas_30d = $sales30
        estoque_minimo = 10 + (($i * 11) % 35)
        lead_time_dias = 2 + ($i % 30)
        unidade_medida = $product.unit
    }

    $alreadyUpdated =
        ([string]$rows[$i].codigo_produto_erp -eq [string]$payload.codigo_produto_erp) -and
        ([string]$rows[$i].codigo_filial_erp -eq [string]$payload.codigo_filial_erp) -and
        ([string]$rows[$i].nome_produto -eq [string]$payload.nome_produto) -and
        ([string]$rows[$i].region_id -eq [string]$payload.region_id)

    if ($alreadyUpdated) {
        continue
    }

    $json = ConvertTo-Json -InputObject $payload -Depth 4 -Compress
    $recordId = [uri]::EscapeDataString([string]$rows[$i].id)
    $updates += [pscustomobject]@{
        Uri = "$apiUrl/$recordId"
        Json = $json
    }
}

$client = New-Object System.Net.Http.HttpClient
$updatedCount = 0
$skippedCount = $expectedCount - $updates.Count

try {
    foreach ($update in $updates) {
        $attempt = 0
        $completed = $false

        while (-not $completed) {
            $attempt++
            $request = New-Object System.Net.Http.HttpRequestMessage
            $request.Method = New-Object System.Net.Http.HttpMethod -ArgumentList 'PATCH'
            $request.RequestUri = [Uri]$update.Uri
            $request.Content = [System.Net.Http.StringContent]::new(
                $update.Json,
                [System.Text.Encoding]::UTF8,
                'application/json'
            )

            $response = $client.SendAsync($request).GetAwaiter().GetResult()
            if (-not $response.IsSuccessStatusCode) {
                $details = $response.Content.ReadAsStringAsync().Result
                $statusCode = [int]$response.StatusCode
                $failedUrl = $request.RequestUri.AbsoluteUri

                if (($statusCode -eq 429 -or $statusCode -ge 500) -and $attempt -lt 8) {
                    $retryAfter = $response.Headers.RetryAfter
                    if ($null -ne $retryAfter -and $null -ne $retryAfter.Delta) {
                        $waitSeconds = [Math]::Max(1, [Math]::Ceiling($retryAfter.Delta.TotalSeconds))
                    } elseif ($null -ne $retryAfter -and $null -ne $retryAfter.Date) {
                        $waitSeconds = [Math]::Max(1, [Math]::Ceiling(($retryAfter.Date.UtcDateTime - [DateTime]::UtcNow).TotalSeconds))
                    } else {
                        $waitSeconds = [Math]::Min(60, [Math]::Pow(2, $attempt))
                    }

                    Write-Warning "HTTP $statusCode; aguardando $waitSeconds s antes de repetir $failedUrl."
                    $response.Dispose()
                    $request.Dispose()
                    Start-Sleep -Seconds $waitSeconds
                    continue
                }

                $response.Dispose()
                $request.Dispose()
                throw "Falha no PATCH $failedUrl : HTTP $statusCode. Resposta: $details. Atualizados nesta execução: $updatedCount; já atualizados antes: $skippedCount."
            }

            $updatedCount++
            $response.Dispose()
            $request.Dispose()
            $completed = $true

            if (($updatedCount % 10) -eq 0 -or $updatedCount -eq $updates.Count) {
                Write-Host "Atualizados nesta execução: $updatedCount de $($updates.Count); já estavam corretos: $skippedCount."
            }

            Start-Sleep -Milliseconds 1200
        }
    }
} finally {
    $client.Dispose()
}

Write-Host "Concluído: $updatedCount atualizados nesta execução; $skippedCount já estavam atualizados; total esperado: $expectedCount."
