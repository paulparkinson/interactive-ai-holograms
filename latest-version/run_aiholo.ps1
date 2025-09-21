# Set the environment variable
$env:SANDBOX_API_URL0 = "http://129.80.247.232:8000/v1/chat/completions"
$env:SANDBOX_API_URL = "http://129.153.130.96/v1/chat/completions?client=server"
$env:SANDBOX_AUTH_TOKEN0 = "Bearer acxsq0n2m9vgj8ev116xaxqg1h3hqmhf"
$env:AI_OPTIMZER = "Bearer 4ouI6wXqONQ4isEX1BUWmx6DiPyh09PPaPK8BjI93ww"
$env:AIHOLO_HOST_URL = "http://localhost:8080"
$env:AUDIO_DIR_PATH = "C:/src/github.com/paulparkinson/interactive-ai-holograms/latest-version/src/main/resources/static/audio-aiholo/"
$env:LANGFLOW_SERVER_URL = "http://141.148.204.74:7860/api"
$env:LANGFLOW_FLOW_ID = "6609ea03-9b37-4f28-9fdf-18cb8fdc504f"
$env:LANGFLOW_API_KEY = "sk-EbE5Pkponr-c8NLZ9hStLG0LmqwM76wvvfIeANaYpI4"
mvn clean package
java -jar .\target\oracleai-0.0.1-SNAPSHOT.jar
