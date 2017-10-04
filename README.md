### Development

For developing you will need to run the following:

``` bash
./scripts/run-against-staging.sh <path/to/access/pem> <path/to/heimdall/pubkey>
```

### Testing

For running local tests you will need to run the following:

``` bash
docker-compose up -d
./scripts/heimdall_tunnel.sh <path/to/access/pem>
```
