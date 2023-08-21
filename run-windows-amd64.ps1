$libFiles = (Get-ChildItem -Path .\lib -Recurse -Include *.jar | ForEach-Object { $_.FullName }) -join ';'
java -cp ".\bin;$libFiles" Main --port 50051 --non-delay 2 -r 1 --mute --limithp 400 400 --grey-bg --inverted-player 1 --grpc-auto --fastmode
# pause