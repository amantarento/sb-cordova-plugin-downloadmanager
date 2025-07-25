var assert = require('nanoassert')
module.exports = new DownloadManager()

function DownloadManager () {
  // Initialize event listeners storage
  this.eventListeners = {};
}

// Add proper event listener management
DownloadManager.prototype.addEventListener = function(eventType, listener) {
  if (!this.eventListeners[eventType]) {
    this.eventListeners[eventType] = [];
  }
  this.eventListeners[eventType].push(listener);
};

DownloadManager.prototype.removeEventListener = function(eventType, listener) {
  if (this.eventListeners[eventType]) {
    const index = this.eventListeners[eventType].indexOf(listener);
    if (index > -1) {
      this.eventListeners[eventType].splice(index, 1);
    }
  }
};

// Initialize event handling when the plugin loads
DownloadManager.prototype.init = function() {
  const self = this;
  
  console.log('DownloadManager initializing event listeners...');
  
  // Set up document event listener for downloadCancelled
  document.addEventListener('downloadCancelled', function(event) {
    console.log('DownloadManager received downloadCancelled event:', event.detail);
    if (self.eventListeners['downloadCancelled']) {
      self.eventListeners['downloadCancelled'].forEach(function(listener) {
        try {
          listener(event.detail);
        } catch (error) {
          console.error('Error in downloadCancelled listener:', error);
        }
      });
    }
  });

  // Fallback for cordova events
  if (window.cordova) {
    // Ensure cordova.fireWindowEvent exists
    if (!window.cordova.fireWindowEvent) {
      window.cordova.fireWindowEvent = function(eventName, data) {
        console.log('Cordova fireWindowEvent called:', eventName, data);
        document.dispatchEvent(new CustomEvent(eventName, { detail: data }));
      };
    }
  }
  
  console.log('DownloadManager event listeners initialized');
};

DownloadManager.prototype.enqueue = function(req, cb) {
  if (typeof req === 'string') req = {uri: req}
  if (req.url) req.uri = req.url

  assert(typeof req.uri === 'string', 'req.uri must be string')
  assert(cb == null ? true : typeof cb === 'function', 'cb must be function')

  assert(req.title == null ? true : typeof req.title === 'string', 'req.title must be string')
  assert(req.description == null ? true : typeof req.description === 'string', 'req.description must be string')
  assert(req.mimeType == null ? true : typeof req.mimeType === 'string', 'req.mimeType must be string')

  assert(req.destinationInExternalFilesDir == null ? true : typeof req.destinationInExternalFilesDir.dirType === 'string', 'req.destinationInExternalFilesDir.dirType must be string')
  assert(req.destinationInExternalFilesDir == null ? true : typeof req.destinationInExternalFilesDir.subPath === 'string', 'req.destinationInExternalFilesDir.subPath must be string')
  assert(req.destinationInExternalPublicDir == null ? true : typeof req.destinationInExternalPublicDir.dirType === 'string', 'req.destinationInExternalPublicDir.dirType must be string')
  assert(req.destinationInExternalPublicDir == null ? true : typeof req.destinationInExternalPublicDir.subPath === 'string', 'req.destinationInExternalPublicDir.subPath must be string')
  assert(req.destinationUri == null ? true : typeof req.destinationUri === 'string', 'req.destinationUri must be string')

  assert(req.visibleInDownloadsUi == null ? true : typeof req.visibleInDownloadsUi === 'boolean', 'req.visibleInDownloadsUi must be boolean')
  assert(req.notificationVisibility == null ? true : Number.isSafeInteger(req.notificationVisibility), 'req.notificationVisibility must be safe integer')

  exec('enqueue', [req], cb)
}

DownloadManager.prototype.query = function(filter, cb) {
  if (typeof filter === 'function') {
    cb = filter
    filter = null
  }

  if (filter == null) filter = {}

  assert(typeof cb === 'function', 'cb must be function')

  exec('query', [filter], cb)
}

DownloadManager.prototype.remove = function(ids, cb) {
  assert(Array.isArray(ids), 'ids must be array')
  assert(cb == null ? true : typeof cb === 'function', 'cb must be function')

  exec('remove', ids, cb)
}

DownloadManager.prototype.addCompletedDownload = function(req, cb) {
  exec('addCompletedDownload', [req], cb)
}

DownloadManager.prototype.fetchSpeedLog = function(cb) {
  exec('fetchSpeedLog', [], cb)
}

function exec (method, args, cb) {
  if (cb == null) cb = noop
  cordova.exec(onsuccess, onerror, 'DownloadManagerPlugin', method, args || [])

  function onsuccess () {
    var args = Array.prototype.slice.call(arguments)
    args.unshift(null)
    cb.apply(null, args)
  }

  function onerror (err) {
    err = (err instanceof Error) ? err : new Error(err)
    cb(err)
  }
}

function noop () {}

// Auto-initialize when the plugin is loaded
if (typeof document !== 'undefined') {
  // Initialize after DOM is ready
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function() {
      module.exports.init();
    });
  } else {
    module.exports.init();
  }
}