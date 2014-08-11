. run.properties

function usage(){
    echo "Usage : $0 start [skipbuild]"
    echo ""
    echo "        Edit run.properties for configuration of ports and domain"
    exit
}

function kill_all_jobs { jobs -p | xargs kill; exit; }
trap kill_all_jobs SIGINT INT

if [ $# -lt 1 ]
then
  usage
fi

originalFolder=`pwd`

case "$1" in
start)
    echo "Starting..."

    cd server-api

    PROPS_FOLDER=src/main/resources/props
    PROPS_FILE=$PROPS_FOLDER/default.props
    mkdir -p $PROPS_FOLDER
    echo "mongo.host=$mongo_host" > $PROPS_FILE
    echo "mongo.port=$mongo_port" >> $PROPS_FILE
    echo "mongo.db=$mongo_db" >> $PROPS_FILE

    echo "auth.enable=$auth_enable" >> $PROPS_FILE
    echo "auth.api.key=$auth_api_key" >> $PROPS_FILE
    echo "auth.source=$auth_source" >> $PROPS_FILE
    echo "auth.ldap.url=$auth_ldap_url" >> $PROPS_FILE
    echo "auth.ldap.principal=$auth_ldap_principal" >> $PROPS_FILE
    echo "auth.whitelist=$auth_whitelist" >> $PROPS_FILE

    echo "https.forcehttpdownload.enable=$https_forcehttpdownload_enable" >> $PROPS_FILE
    echo "https.forcehttpdownload.domain=$https_forcehttpdownload_domain" >> $PROPS_FILE

    if [ "$2" != "skipbuild" ]
    then
      ./sbt clean assembly
    fi
    export PORT=$api_port
    java -Dfile.encoding=UTF-8 -jar target/scala-2.9.2/*-assembly-*.jar &

    cd ../server-web/app/
    python -m SimpleHTTPServer $web_port &

    wait
    ;;
*)    usage ;;
esac

cd $originalFolder
