    
    import Foundation
    import MZDownloadManager
    
    struct RequestObject: Codable {
        let uri, description, title, mimeType: String
        let visibleInDownloadsUi: Bool
        let notificationVisibility: Int
        let headers: [Header]
        let destinationInExternalFilesDir: DestinationInExternalFilesDir
    }
    
    struct Header: Codable {
    }
    
    struct DestinationInExternalFilesDir: Codable {
        let dirType: String, subPath: String
    }
        
    let statusToCodeMapping = ["GettingInfo": 0x00000001, "Downloading": 0x00000002, "Paused": 0x00000004, "Failed": 0x00000010, "Downloaded": 0x00000008]
    
    @objc(DownloadManagerPlugin) class DownloadManagerPlugin : CDVPlugin {
        
        var downloadManager: MZDownloadManager?
        var allDownloads: [MZDownloadModel] = []
        var applicationDirectoryURL: URL?
        
        override func pluginInitialize() {
            do {
                let sessionIdentifer: String = "org.sunbird.ios.BackgroundSession"
                let fileManager = FileManager.default
                self.applicationDirectoryURL = try fileManager.url(for: .documentDirectory, in: .userDomainMask, appropriateFor: nil, create: false)
                self.downloadManager = MZDownloadManager(session: sessionIdentifer, delegate: self)
                self.allDownloads = []
            } catch let error {
                print("Failed to initialize Download Manager")
                print ("file error: \(error)")
            }
        }
        
        @objc
        func enqueue(_ command: CDVInvokedUrlCommand) {
            var pluginResult: CDVPluginResult = CDVPluginResult.init(status: CDVCommandStatus_ERROR)
            let requestObject = command.arguments[0] as? [String: Any]
            if let fileName = requestObject?["title"] as? String, let fileUrl = requestObject?["uri"] as? String {
                var downloadDirectory = "Download"
                if let externalDirObject = requestObject?["destinationInExternalFilesDir"] as? [String: String] {
                    if let dirType = externalDirObject["dirType"] {
                        downloadDirectory = dirType
                    }
                }
                do {
                    if let applicationDirectoryURL = self.applicationDirectoryURL {
                        let fileManager = FileManager.default
                        let downloadsDirectoryPathURL = applicationDirectoryURL.appendingPathComponent(downloadDirectory)
                        var isDir: ObjCBool = true
                        if !fileManager.fileExists(atPath: downloadsDirectoryPathURL.path, isDirectory: &isDir) {
                            try fileManager.createDirectory(atPath: downloadsDirectoryPathURL.path,
                                                            withIntermediateDirectories: true, attributes: nil)
                        }
                        if let downloadManager = self.downloadManager {
                            downloadManager.addDownloadTask(fileName, fileURL: fileUrl, destinationPath: downloadsDirectoryPathURL.path)
                            pluginResult = CDVPluginResult.init(status: CDVCommandStatus_OK, messageAs: fileName)
                        }
                    }
                } catch let error {
                    print("Failed at enqueue method", error)
                }
            }
            self.commandDelegate.send(pluginResult, callbackId: command.callbackId)
        }
        
        
        private func convertSizeToBytes(_ size: Float, _ unit: String) -> Float {
            var result = size
            if unit.lowercased() == "kb" {
                result = result * 1024
            } else if unit == "mb" {
                result = result * 1024 * 1024
            } else if unit == "gb" {
                result = result * 1024 * 1024 * 1024
            }
            return result
        }
        
        @objc
        func query(_ command: CDVInvokedUrlCommand) {
            var pluginResult: CDVPluginResult = CDVPluginResult.init(status: CDVCommandStatus_ERROR)
            var queryRequests = command.arguments as? [[String: Any]]
            var queryResponse: [[String: Any]] = []
            if !(queryRequests?.isEmpty ?? true) && queryRequests != nil {
                for query in queryRequests! {
                    if let fileNames = query["ids"] as? [String] {
                        for fileName in fileNames {
                            if let activeDownloads = self.downloadManager?.downloadingArray {
                                var downloadModel: MZDownloadModel?;
                                if !activeDownloads.isEmpty {
                                    if let index = activeDownloads.firstIndex(where: {$0.fileName == fileName}) {
                                        downloadModel = activeDownloads[index]
                                        
                                    }
                                } else if !self.allDownloads.isEmpty {
                                    if let index = self.allDownloads.firstIndex(where: {$0.fileName == fileName}) {
                                        downloadModel = self.allDownloads[index]
                                    }
                                }
                                if downloadModel != nil {
                                    
                                    if statusToCodeMapping.keys.contains(downloadModel!.status) {
                                        downloadModel?.status = String(statusToCodeMapping[downloadModel!.status]!)
                                    }
                                    
                                    var result: [String: Any] = [:]
                                    result["status"] = Int(downloadModel!.status)
                                    result["totalSizeBytes"] = self.convertSizeToBytes(downloadModel?.file?.size ?? 0, downloadModel?.file?.unit ?? "bytes")
                                    result["bytesDownloadedSoFar"] = self.convertSizeToBytes(downloadModel?.downloadedFile?.size ?? 0, downloadModel?.downloadedFile?.unit ?? "bytes")
                                    queryResponse.append(result)
                                }
                            }
                        }
                    }
                }
            }
            
            if !queryResponse.isEmpty {
                pluginResult = CDVPluginResult.init(status: CDVCommandStatus_OK, messageAs: queryResponse)
            }
            self.commandDelegate.send(pluginResult, callbackId: command.callbackId)
        }
        
        @objc
        func remove(_ command: CDVInvokedUrlCommand) {
            var filesToRemove = command.arguments as! [String]
            var removeCount = 0
            if !filesToRemove.isEmpty {
                for file in filesToRemove {
                    do {
                        let directory = "Download"
                        if let activeDownloads = self.downloadManager?.downloadingArray {
                            if !activeDownloads.isEmpty {
                                if let index = activeDownloads.firstIndex(where: {$0.fileName == file}) {
                                    self.downloadManager?.cancelTaskAtIndex(index)
                                }
                            }
                        }
                        if let applicationDirectory = self.applicationDirectoryURL {
                            let fileManager = FileManager.default
                            let downloadDirectory = applicationDirectory.appendingPathComponent(directory)
                            let filePath = downloadDirectory.appendingPathComponent(file)
                            if fileManager.fileExists(atPath: filePath.path) {
                                try fileManager.removeItem(atPath: filePath.path)
                                removeCount = removeCount + 1
                            }
                        }
                    } catch let error {
                        print(error)
                        print("failed to remove file \(file)")
                    }
                }
            }
            var pluginResult: CDVPluginResult = CDVPluginResult.init(status: CDVCommandStatus_OK, messageAs: removeCount)
            self.commandDelegate.send(pluginResult, callbackId: command.callbackId)
        }
        
        @objc
        func addCompletedDownload(_ command: CDVInvokedUrlCommand) {
            //This method is not being used. Hence skipping the implementation
            var pluginResult: CDVPluginResult = CDVPluginResult.init(status: CDVCommandStatus_ERROR)
            pluginResult = CDVPluginResult.init(status: CDVCommandStatus_OK, messageAs: "")
            self.commandDelegate.send(pluginResult, callbackId: command.callbackId)
            
        }
        
        @objc
        func fetchSpeedLog(_ command: CDVInvokedUrlCommand) {
            var totalBytesDownloaded: Float = 0
            for download in self.allDownloads {
                let downloadedSize = download.downloadedFile?.size ?? 0
                let downloadUnit = download.downloadedFile?.unit ?? "bytes"
                totalBytesDownloaded = totalBytesDownloaded + self.convertSizeToBytes(downloadedSize, downloadUnit)
            }
            let totalKBDownloaded = totalBytesDownloaded / 1024
            let result = ["totalKBDownloaded": totalKBDownloaded]
            
            //TODO logic to add distribution key
            
            var pluginResult: CDVPluginResult = CDVPluginResult.init(status: CDVCommandStatus_OK, messageAs: result)
            self.commandDelegate.send(pluginResult, callbackId: command.callbackId)
            
        }
    }
    
    extension DownloadManagerPlugin: MZDownloadManagerDelegate {
        
        func downloadRequestStarted(_ downloadModel: MZDownloadModel, index: Int) {
            self.allDownloads.append(downloadModel)
        }
        
        func downloadRequestDidPopulatedInterruptedTasks(_ downloadModels: [MZDownloadModel]) {
            print("downloadRequestDidPopulatedInterruptedTasks")
            print(downloadModels)
        }
        
        func downloadRequestDidUpdateProgress(_ downloadModel: MZDownloadModel, index: Int) {
            print("downloadRequestDidUpdateProgress")
            print(downloadModel)
        }
        
        func downloadRequestDidPaused(_ downloadModel: MZDownloadModel, index: Int) {
            if !self.allDownloads.isEmpty {
                if let index = self.allDownloads.firstIndex(where:  {$0.fileName == downloadModel.fileName}) {
                    let status =  String(statusToCodeMapping["Paused"]!)
                    self.allDownloads[index].status = status
                }
            }
        }
        
        func downloadRequestDidResumed(_ downloadModel: MZDownloadModel, index: Int) {
            if !self.allDownloads.isEmpty {
                if let index = self.allDownloads.firstIndex(where:  {$0.fileName == downloadModel.fileName}) {
                    let status =  String(statusToCodeMapping["Downloading"]!)
                    self.allDownloads[index].status = status
                }
            }
        }
        
        func downloadRequestCanceled(_ downloadModel: MZDownloadModel, index: Int) {
            if !self.allDownloads.isEmpty {
                if let index = self.allDownloads.firstIndex(where:  {$0.fileName == downloadModel.fileName}) {
                    let status =  String(statusToCodeMapping["Failed"]!)
                    self.allDownloads[index].status = status
                }
            }
        }
        
        func downloadRequestFinished(_ downloadModel: MZDownloadModel, index: Int) {
            if !self.allDownloads.isEmpty {
                if let index = self.allDownloads.firstIndex(where:  {$0.fileName == downloadModel.fileName}) {
                    let status =  String(statusToCodeMapping["Downloaded"]!)
                    self.allDownloads[index].status = status
                }
            }
        }
        
        func downloadRequestDidFailedWithError(_ error: NSError, downloadModel: MZDownloadModel, index: Int) {
            if !self.allDownloads.isEmpty {
                if let index = self.allDownloads.firstIndex(where:  {$0.fileName == downloadModel.fileName}) {
                    let status =  String(statusToCodeMapping["Failed"]!)
                    self.allDownloads[index].status = status
                }
            }
        }
        
        func downloadRequestDestinationDoestNotExists(_ downloadModel: MZDownloadModel, index: Int, location: URL) {
            if let applicationDir = self.applicationDirectoryURL {
                let myDownloadPath = applicationDir.appendingPathComponent("Download")
                if !FileManager.default.fileExists(atPath: myDownloadPath.path) {
                    try! FileManager.default.createDirectory(atPath: myDownloadPath.path, withIntermediateDirectories: true, attributes: nil)
                }
                let filePath = myDownloadPath.appendingPathComponent(downloadModel.fileName).path
                try! FileManager.default.moveItem(at: location, to: URL(fileURLWithPath: filePath))
                debugPrint("Default folder path: \(myDownloadPath)")
            }
        }
    }
