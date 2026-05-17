# Kubernetes manifests (Minikube)

## One-time setup

```bash
minikube start
# Point your shell's docker at Minikube's daemon so the image is visible to the cluster:
eval $(minikube docker-env)         # bash/zsh
# minikube docker-env | Invoke-Expression   # PowerShell

docker build -t restfulremedy:latest .
```

## Deploy

```bash
kubectl apply -f k8s/namespace.yaml
# Edit k8s/secrets.yaml first to set your real CLAUDE_KEY!
kubectl apply -f k8s/secrets.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/postgres.yaml
kubectl apply -f k8s/app.yaml
```

## Access

```bash
minikube service restfulremedy -n restfulremedy --url
```

## Inspect

```bash
kubectl get pods -n restfulremedy
kubectl logs -n restfulremedy deploy/restfulremedy -f
kubectl logs -n restfulremedy deploy/postgres -f
```

## Tear down

```bash
kubectl delete namespace restfulremedy
```
