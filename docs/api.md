# Witan HTTP API Documentation

## How to inspect all the API endpoints with Swagger.

If you want a visual overview of the complete API reference then the Witan API is availble with a Swagger reference implementation. This will give you the opportunity to test out elements of the API without any programming. Swagger is interactive, meaning that you can test out API calls by filling in the fields provided. You will be required to login with your credentials before use as the authentication token is required in the other API calls.


### Example: Looking at the Swagger docs for the API.

```
https://api.witanforcities.com
```

When you visit the URL you will see the following screen. 

![api-screenshot.png](api-screenshot.png)

Clicking on the relevant API routes will expand into an information window where see the required fields and try out the API.

## How to check the API is healthy.

The healthcheck is a quick way to find out if the API endpoint is alive. If successful it will return `hello`.

### Example: Checking if the API is healthy.
```
curl -vvv -XGET https://api.witanforcities.com/healthcheck
```

### Example response: 

`hello`


## How to authenticate a user.

All calls to the API require authorisation based on your Witan login. You will receive an `auth-token` that will be used against subsequent API calls and a refresh token.

If the authorisation token is unused for longer than 30 minutes you will be required to refresh your token using the `/api/refresh` endpoint. For those interested in building a user interface that uses the Witan API then using the refresh token is the best way to keep your auth token alive. For those who are using code to call the API then it's assumed that you'll be using the login then use the auth token in subsequent calls.

### Example: User login to get authentication token

Using JSON the API expects the following payload. 

```
{"username":"yourloginemail@domain.com",
 "password":"your_password"}
```

The `curl` example below shows the payload being sent to the API.

```
curl -X POST --header 'Content-Type: application/json' --header 'Accept: application/json' -d '{"username":"yourloginemail@domain.com","password":"your_password"}' 'https://api.witanforcities.com/api/login'
```

### Example response

```
{"token-pair":
   {"auth-token":"eyJh..........VdbpA",
    "refresh-token":"eyJhb........sr4A"
   }
}
```

## How the receipt mechanism works.

The Witan API is based on an asynchronus system. For aspects like uploads for example the API will issue you a receipt id. You are expected to use this receipt along with your auth token to query the API until the action is complete.

For example: you want to upload a file, once you've authenticated your user and have an auth token you will then call `/api/files/upload` with your auth token as a header. In response the API will send back a `receipt-id` value and a 202 response code. 

While Witan processes your file upload request you are expected to use the endpoint `/api/receipts/[receipt-id]` to see if your upload request url is ready. If Witan has successfully processed created an upload link you will receive a 200 response code along with upload link and filestore id. 

Once you have a 200 response and the required url/id combination you can safely upload your file. 

## How to upload a file.

The file upload aspect of the Witan API is not uploading files in the traditional sense. The API provides a mechanism to generate a URL and file id to upload a file to. The actual uploading of the file, and how that is executed, is up to you. 

### Step 1 - Generate your receipt-id.

Assuming that you have logged in and have an auth token the first step is to do a POST to the `/api/file/upload` endpoint. 

```
curl -X POST --header 'Content-Type: application/json' --header 'Accept: application/json' --header 'authorization: eyJhb.....7SeMQ' 'https://api.witanforcities.com/api/files/upload'
```

This will return a receipt-id, using this id you can now poll against the API to get the file upload link.

```
curl -X GET --header 'Accept: application/json' --header 'authorization: eyJhb.....7SeMQ' 'https://api.witanforcities.com/api/receipts/abcdefg-abcd-abcd-abcd-abcdeghij'
```

### Step 2 - Get the upload link.

The response from the `/api/receipt/[receipt-id]` call will respond with either a 202 code, meaning the process is still being handled by Witan, you should continue to call the receipt endpoint with the same URL to see if the receipt has updated. 

If you receive a response with a 200 status code then the request is classed as complete and you will see the following response from the API. 

```
{
  "witan.httpapi.spec/uri": "https://prod-witan-kixi-datastore-filestore...........",
  "kixi.datastore.filestore/id": "d1bec41e.....46c2"
}
```

It is your responsibility to upload the file you want stored on Witan using the URL that is in the `witan.httpapi.spec/url` response field. 

```
curl -vvv -XPUT --data-binary /Users/jasonbell/Downloads/resp_gas.xml "https://prod-witan-kixi-datastore-filestore.s3-eu-west-1.amazonaws.com/d1bec41e-086e-4ad7-
```

### Step 3 - Post the metadata about that file.


## How to find your files.




## How to download a file.


## How to share a file with another user.


# Known Issues
