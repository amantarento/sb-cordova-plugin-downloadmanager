# sb-cordova-plugin-downloadmanager
A plugin to download files.

## Installation

    cordova plugin add https://github.com/Sunbird-Ed/sb-cordova-plugin-downloadmanager.git#<branch_name>

To install it locally 

Clone the repo then execute the following command
    
    cordova plugin add <location_of_plugin>/sb-cordova-plugin-downloadmanager

# API Reference


* [downloadManager](#module_downloadManager)
    * [.enqueue(request, successCallback)](#module_downloadManager.enqueue)
    * [.query(filter, successCallback)](#module_downloadManager.query)
    * [.remove(ids, successCallback)](#module_downloadManager.remove)
    * [.fetchSpeedLog(successCallback)](#module_downloadManager.fetchSpeedLog)


## downloadManager
### downloadManager.enqueue(request, successCallback)

Enqueues the request to be downloaded.

- `request` represents reuest to download a file.

### downloadManager.query(filter, successCallback)
Queries the file in the queue

- `filter` represents filter to be applied to find the file.

### downloadManager.remove(ids, successCallback)
Removes the ids from queue,

- `ids` represents path of the directory.


### downloadManager.fetchSpeedLog(successCallback)

Logs the speed of the network.

