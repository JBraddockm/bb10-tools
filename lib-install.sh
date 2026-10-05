for f in libs/*.jar; do
  base=$(basename "$f" .jar)

  if [[ "$base" =~ ^(.+)-([0-9][0-9.]*)$ ]]; then
    artifact="${BASH_REMATCH[1]}"; version="${BASH_REMATCH[2]}"
  else
    artifact="$base"; version="1.0"
  fi

  mvn install:install-file \
    -Dfile="$f" \
    -DgroupId=local.qconn \
    -DartifactId="$artifact" \
    -Dversion="$version" \
    -Dpackaging=jar \
    -DlocalRepositoryPath=libs-repo \
    -DgeneratePom=true
done