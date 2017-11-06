import requests
import os


#specify user account (email) and password
user = ""
pwd = ""


###generate authorization and refresh tokens
url = 'https://api.witanforcities.com/api/login'
headers = {'Content-Type': 'application/json',
           'Accept': 'application/json',
           }
data = '{"username":"'+user+'", "password":"'+pwd+'"}'

r = requests.post(url, headers=headers, data=data)
print(r.text)

auth_token = r.json()['token-pair']['auth-token']
refresh_token = r.json()['token-pair']['refresh-token']


###generate reciept id
url = 'https://api.witanforcities.com/api/files/upload'
headers = {'Content-Type': 'application/json',
           'Accept': 'application/json',
           'authorization': auth_token,
           }

r = requests.post(url, headers=headers)
print(r.text)

receipt_id = r.json()['receipt-id']


###file upload link
url = 'https://api.witanforcities.com/api/receipts/'+receipt_id
headers = {'Accept': 'application/json',
           'authorization': auth_token,
           }

r = requests.get(url, headers=headers)
print(r.status_code)
print(r.text)
upload_url = r.json()['witan.httpapi.spec/uri']
file_id = r.json()['kixi.datastore.filestore/id']


###upload a file
url = upload_url
#specify upload file path
file = ""

file_size = str(os.path.getsize(file))
file_name = os.path.split(file)[1]
file_type = os.path.splitext(file)[1].upper().replace(".","")

#note that requests.post(url, data=file) gives a 403 error becuase data needs to be passed as binary.
with open(file, 'rb') as data:
    print(requests.put(url, data=data))
    
   
###send metadata
url = 'https://api.witanforcities.com/api/files/'+file_id+'/metadata'
    
headers = {'Content-Type': 'application/json',
           'Accept': 'application/json',
           'authorization': auth_token,
           }

data = '{"kixi.datastore.metadatastore/header":false, \
        "kixi.datastore.metadatastore/size-bytes":'+file_size+', \
        "kixi.datastore.metadatastore/name":"'+file_name+'", \
        "kixi.datastore.metadatastore/file-type":"'+file_type+'"}'

r = requests.put(url, headers=headers, data=data)

if r.json()['receipt-id'] != None:
    print("Upload sucessful!")
    
        
###get list of files
url = 'https://api.witanforcities.com/api/files'

headers = {'Accept': 'application/json',
           'authorization': auth_token,
           }

r = requests.get(url, headers=headers)
r.json()['paging']


###get nth file's receipt-id
n = 0
file_id = r.json()['files'][n]['kixi.datastore.metadatastore/id']
url = 'https://api.witanforcities.com/api/files/'+file_id+'/link'

headers = {'Content-Type': 'application/json',
           'Accept': 'application/json',
           'authorization': auth_token,
           }

r = requests.post(url, headers=headers)
receipt_id = r.json()['receipt-id'] 


###get download link

url = 'https://api.witanforcities.com/api/receipts/'+receipt_id

headers = {'Accept': 'application/json',
           'authorization': auth_token,
           }

r = requests.get(url, headers=headers)
download_url = r.json()['witan.httpapi.spec/uri']


###name and download file
#specify file save path
file = ""

with open (file, 'wb') as output:
    output.write(requests.get(download_url).content)





    






