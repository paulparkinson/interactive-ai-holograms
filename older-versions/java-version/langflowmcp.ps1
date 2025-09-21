$env:LANGFLOW_API_KEY = "sk-EbE5Pkponr-c8NLZ9hStLG0LmqwM76wvvfIeANaYpI4"

# Check if the environment variable is set
if (-not $env:LANGFLOW_API_KEY) {
    Write-Error "LANGFLOW_API_KEY environment variable not found"
    exit 1
}

# Send the POST request
$response = Invoke-RestMethod -Uri "http://141.148.204.74:7860/api/v1/run/6609ea03-9b37-4f28-9fdf-18cb8fdc504f?stream=false" `
    -Method Post `
    -Headers @{
        "Content-Type" = "application/json"
        "x-api-key"    = $env:LANGFLOW_API_KEY
    } `
    -Body $jsonData

# Inspect the structure – optional
$response | ConvertTo-Json -Depth 10  # shows nested outputs/results

# Extract the chat message
# Some flows return the message in "outputs", others in "results".
if ($response.outputs -and $response.outputs[0].outputs) {
    # Simple Agent / Chat Output pattern (from docs)
    $replyText = $response.outputs[0].outputs[0].outputs.message.message
} elseif ($response.outputs -and $response.outputs[0].outputs[0].results) {
    # Basic Prompting / different flow pattern
    $replyText = $response.outputs[0].outputs[0].results.message.text
} else {
    $replyText = "Could not locate message in response."
}

Write-Output "Response: $replyText"
