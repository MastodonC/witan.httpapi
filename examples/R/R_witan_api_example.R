library(httr)
library(jsonlite)


test_witan_api_health <- function() {
  response <- GET("https://api.witanforcities.com/healthcheck")
  if (readBin(r$content, character()) == "hello") {
    print("Witan API is functioning normally")
    } else {
    print(paste("Error: Witan API is not functioning normally.")) # The healthcheck returned status code ", toString(response$status_code)))
      #should add handling incase no status_code is returned
  }
}


get_witan_auth_token <- function(username, password) {
  data <- list(username = username, password = password)
  response <- POST("https://api.witanforcities.com/api/login", accept_json(), content_type_json(), body = data, encode = "json")
  content <- fromJSON(readBin(response$content, character()))
  content$`token-pair`$`auth-token`
}


get_witan_upload_receipt_id <- function(auth_token) {
  response <- POST("https://api.witanforcities.com/api/files/upload", accept_json(), content_type_json(), add_headers(authorization = auth_token), encode = "json") 
  content <- fromJSON(readBin(response$content, character()))
  content$`receipt-id`
}


get_witan_file_upload_link <- function(auth_token, receipt_id) {
  response <- GET(paste("https://api.witanforcities.com/api/receipts/", receipt_id, sep = ""), accept_json(), content_type_json(), add_headers(authorization = auth_token), encode = "json") 
  content <- fromJSON(readBin(response$content, character()))
  list(upload_url = content$`witan.httpapi.spec/uri`, file_id = content$`kixi.datastore.filestore/id`)
}





