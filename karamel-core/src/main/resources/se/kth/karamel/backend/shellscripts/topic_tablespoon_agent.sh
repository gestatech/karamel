echo $$ > %pid_file%; echo '#!/bin/bash
%sudo_command% cat > /usr/local/tablespoon-agent/topics/%file_name%.json << EOF
%file_content%
EOF
echo '%task_id%' >> %succeedtasks_filepath%
' > agent-topic.sh ; chmod +x agent-topic.sh ; ./agent-topic.sh