#!/bin/bash

# script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"
CLASS_PATH="$SCRIPT_DIR/../bin"

if [[ "$OSTYPE" == "linux-gnu" ]]; then
    function check_port {
        netstat -tapn 2>/dev/null | grep $1 2>/dev/null | grep LISTEN &>/dev/null
        [[ $? -eq 0 ]]
    }
elif [[ "$OSTYPE" == "darwin"* ]]; then
    function check_port {
        lsof -nP -iTCP:$1 2>/dev/null | grep LISTEN &>/dev/null
        [[ $? -eq 0 ]]
    }
else
    echo "Unsupported OS: $OSTYPE"
    exit;
fi

if [ "$#" -ne 2 ]; then
    echo "usage: $0 <time to simulate system (seconds)> <test script file>"
    exit;
fi

OUTPUT_FILE="$SCRIPT_DIR/../serverlogs/server.output"
echo "Writing simulation output to $OUTPUT_FILE"
echo "" > $OUTPUT_FILE

TIME_TO_SIMULATE="$1"
echo "Simulation will last $TIME_TO_SIMULATE seconds"
echo "TIME_TO_SIMULATE=$TIME_TO_SIMULATE" >> "$OUTPUT_FILE"

TEST_FILE="$2"
echo "Using test file $TEST_FILE"
NUM_SERVERS=0

RMI_PORT="1098"

LOG_DIR="$SCRIPT_DIR/../serverlogs"
echo "Reading log files from $LOG_DIR"
CONFIG_DIR="$SCRIPT_DIR/../serverlogs"
echo "Reading config file from $CONFIG_DIR"

echo "Restarting rmiregistry"
pkill -9 rmiregistry
sleep 0.5
if check_port $RMI_PORT; then
    tput setaf 1
    echo "Something other than RMI Registry is using port $RMI_PORT"
    echo "Please check if another program is listening on $RMI_PORT, and kill it if necessary."
    echo "Otherwise, set a different port in the \$RMI_PORT field in $0"
    tput sgr 0
    exit;
fi

cd "$SCRIPT_DIR"
rmiregistry "$RMI_PORT" &
while true; do
    if check_port $RMI_PORT; then
        break
    fi
    echo "Waiting for rmiregistry to bind to port $RMI_PORT"
    sleep 1
done

declare -a SERVER_PIDS

function restart_server {
    id=$1
    if [ -z "${SERVER_PIDS[$id]}" ]
    then
	java -classpath "$CLASS_PATH" \
        edu.duke.raft.StartServer \
        "$RMI_PORT" \
        "$id" \
        "$LOG_DIR" \
        "$CONFIG_DIR" >> "$OUTPUT_FILE" &
	PID="$!"
	SERVER_PIDS[$id]="$PID"
	echo "Started server S$id"
    fi
}

function start_servers {
    NUM_SERVERS=$1
    for (( id=1; id<=$NUM_SERVERS; id++ ))
    do
	# initialize servers' log and config files
    if [ ! -f "$LOG_DIR/$id.init.log" ]; then
        cp "$LOG_DIR/init.log" "$LOG_DIR/$id.init.log"
    fi
    cp "$LOG_DIR/$id.init.log" "$LOG_DIR/$id.log"
	if [ ! -f "$LOG_DIR/$id.init.config" ]; then
	    cp "$CONFIG_DIR/init.config" "$CONFIG_DIR/$id.init.config"
	fi
    cp "$LOG_DIR/$id.init.config" "$LOG_DIR/$id.config"

	echo "NUM_SERVERS=$NUM_SERVERS" >> "$CONFIG_DIR/$id.config"
	restart_server $id
    done
}

function pause_server {
    kill -SIGSTOP ${SERVER_PIDS[$1]}
    echo "Paused server S$1"
}

function resume_server {
    kill -SIGCONT ${SERVER_PIDS[$1]}
    echo "Resumed server S$1"
}

function fail_server {
    kill -9 ${SERVER_PIDS[$1]}
    SERVER_PIDS[$1]=""
    echo "Failed server S$1"
}

START=`date +%s`
# load the test script
if [ -e "$SCRIPT_DIR/../$TEST_FILE" ]; then
    source "$SCRIPT_DIR/../$TEST_FILE"
    while [ $(( $(date +%s) - $TIME_TO_SIMULATE )) -lt $START ]
    do
	sleep 5
    done
else
    echo "Could not find test file $SCRIPT_DIR/../$TEST_FILE"
fi

echo "Shutting down simulation"

for (( id=1; id<=$NUM_SERVERS; id++ ))
do
    echo "Shutting down server S$id"
    kill -9 ${SERVER_PIDS[$id]}
done
