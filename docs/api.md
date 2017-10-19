# Witan HTTP API Documentation

## Inspecting with Swagger

If you want a visual overview of the complete API reference then the Witan API is availble with a Swagger reference implementation. This will give you the opportunity to test out elements of the API without any programming.

You will be required to login with your credentials before use.

```
https://api.witanforcities.com
```

## API Healthcheck

The healthcheck is a quick way to find out if the API endpoint is alive. If successful it will return `hello`.

```
https://api.witanforcities.com/healthcheck
```

## Authentication and Tokens

All calls to the API require authorisation based on your Witan login. You will receive an `auth-token` that will be used against subsequent API calls and a refresh token.

If the authorisation token is unused for longer than 30 minutes you will be required to refresh your token using the `/api/refresh` endpoint.


## Files

## Uploads

## Downloads

## Metadata

## Receipts

## Errors

## Sharing

## Links
