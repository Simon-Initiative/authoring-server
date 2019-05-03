#!/usr/bin/jjs -fv
var cmd = "mvn clean package; docker build -t oli/content-service .";
$EXEC(cmd);
print($OUT);
if ($ERR) {
    print($ERR);
}

