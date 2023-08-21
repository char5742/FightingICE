$libFiles = (Get-ChildItem -Path .\lib -Recurse -Include *.jar | ForEach-Object { $_.FullName }) -join ';'
java -cp ".\bin;$libFiles" Main --port 50005 --non-delay 2 --mute --limithp 400 400 --grey-bg --inverted-player 1 --grpc
# pause