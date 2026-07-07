#!/usr/bin/env bash
# EC2 인스턴스(단일 노드)에 k3s를 설치한다. SSH로 EC2에 접속한 뒤 이 스크립트를 실행하라.
set -euo pipefail

curl -sfL https://get.k3s.io | sh -

echo "k3s 설치 완료. 상태 확인:"
sudo k3s kubectl get nodes

echo
echo "kubeconfig 위치: /etc/rancher/k3s/k3s.yaml"
echo "로컬 머신에서 원격으로 kubectl을 쓰려면:"
echo "  scp ec2-user@<EC2_IP>:/etc/rancher/k3s/k3s.yaml ~/.kube/chapchu-config"
echo "  sed -i 's/127.0.0.1/<EC2_IP>/' ~/.kube/chapchu-config"
echo "  export KUBECONFIG=~/.kube/chapchu-config"
