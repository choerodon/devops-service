#!/usr/bin/env sh
cd /tmp/kubeadm-ha && ansible-playbook -i /tmp/k8s-inventory.ini {{command}} >{{log-path}} 2>&1
exitCode=$?
echo $exitCode >{{exit-code-path}}
if [ $exitCode != 0 ]; then
  cat /tmp/install.log >&2
  exit 1
fi
