/**
 * Copyright (c) 2013 Continuuity, Inc.
 * Base server used for developer and enterprise editions. This provides common functionality to
 * set up a node js server with socket io and define routes. All custom functionality to an edition
 * must be placed under the server file inside the edition folder.
 */

var express = require('express'),
  io = require('socket.io'),
  Int64 = require('node-int64').Int64,
  fs = require('fs'),
  log4js = require('log4js'),
  http = require('http'),
  https = require('https');

var Api = require('../common/api');

/**
 * Generic web app server. This is a base class used for creating different editions of the server.
 * This provides base server functionality, logging, routes and socket io setup.
 * @param {string} dirPath from where module is instantiated. This is used becuase __dirname defaults
 *    to the location of this module.
 * @param {string} logLevel log level {TRACE|INFO|ERROR}
 */
var WebAppServer = function(dirPath, logLevel) {
  this.dirPath = dirPath;
  this.LOG_LEVEL = logLevel;
};

/**
 * Thrift API service.
 */
WebAppServer.prototype.Api = Api;

/**
 * Server version.
 */
WebAppServer.prototype.VERSION = '';

/**
 * Express app framework.
 */
WebAppServer.prototype.app = express();

/**
 * Socket io.
 */
WebAppServer.prototype.io = {};

/**
 * Socket to listen to emit events and data.
 */
WebAppServer.prototype.socket = null;

/**
 * Config.
 */
WebAppServer.prototype.config = {};

/**
 * Configuration file pulled in and set.
 */
WebAppServer.prototype.configSet = false;

/**
 * Sets version if a version file exists.
 */
WebAppServer.prototype.setVersion = function() {
  try {
    this.VERSION = fs.readFileSync(this.dirPath + '../../../VERSION', 'utf8');
  } catch (e) {
    this.VERSION = 'UNKNOWN';
  }
}

/**
 * Configures logger.
 * @param {string} opt_appenderType log4js appender type.
 * @param {string} opt_logger log4js logger name.
 * @return {Object} instance of logger.
 */
WebAppServer.prototype.getLogger = function(opt_appenderType, opt_loggerName) {
  var appenderType = opt_appenderType || 'console';
  var loggerName = opt_loggerName || 'Developer UI';
  console.log(loggerName);
  log4js.configure({
    appenders: [
      {type: appenderType}
    ]
  });
  var logger = log4js.getLogger(loggerName);
  logger.setLevel(this.LOG_LEVEL);
  return logger;
};

/**
 * Configures express server.
 */
WebAppServer.prototype.configureExpress = function() {
  this.app.use(express.bodyParser());

  // Workaround to make static files work on cloud.
  if (fs.existsSync(this.dirPath + '/../client/')) {
    this.app.use(express.static(this.dirPath + '/../client/'));
  } else {
    this.app.use(express.static(this.dirPath + '/../../client/'));
  }
};

/**
 * Creates http server based on app framework.
 * Currently works only with express.
 * @param {Object} app framework.
 * @return {Object} instance of the http server.
 */
WebAppServer.prototype.getServerInstance = function(app) {
  return http.createServer(app);
};

/**
 * Opens an io socket using the server.
 * @param {Object} Http server used by application.
 * @return {Object} instane of io socket listening to server.
 */
WebAppServer.prototype.getSocketIo = function(server) {
  var io = require('socket.io').listen(server);
  io.configure('development', function(){
    io.set('transports', ['websocket', 'xhr-polling']);
    io.set('log level', 1);
  });
  return io;
};

/**
 * Defines actions in response to a recieving data from a socket.
 * @param {Object} request a socket request.
 * @param {Object} error error.
 * @param {Object} response for hte socket request.
 */
WebAppServer.prototype.socketResponse = function(request, error, response) {
  this.socket.emit('exec', error, {
    method: request.method,
    params: typeof response === "string" ? JSON.parse(response) : response,
    id: request.id
  });
};

/**
 * Configures socket io handlers. Async binds socket io methods.
 * @param {Object} instance of the socket io.
 * @param {string} name of evn for socket to emit.
 * @param {string} version.
 */
WebAppServer.prototype.configureIoHandlers = function(io, name, version) {
  var self = this;
  io.sockets.on('connection', function (newSocket) {

    self.socket = newSocket;
    self.socket.emit('env',
                          {"name": name, "version": version, "credential": self.Api.credential });

    self.socket.on('metadata', function (request) {
      self.Api.metadata(version, request.method, request.params, function (error, response) {
        self.socketResponse(request, error, response);
      });
    });

    self.socket.on('far', function (request) {
      self.Api.far(version, request.method, request.params, function (error, response) {
        self.socketResponse(request, error, response);
      });
    });

    self.socket.on('gateway', function (request) {
      self.Api.gateway('apikey', request.method, request.params, function (error, response) {
        self.socketResponse(request, error, response);
      });
    });

    self.socket.on('monitor', function (request) {
      self.Api.monitor(version, request.method, request.params, function (error, response) {
        self.socketResponse(request, error, response);
      });
    });

    self.socket.on('manager', function (request) {
      self.Api.manager(version, request.method, request.params, function (error, response) {

        if (response && response.length) {
          var int64values = {
            "lastStarted": 1,
            "lastStopped": 1,
            "startTime": 1,
            "endTime": 1
          };
          for (var i = 0; i < response.length; i ++) {
            for (var j in response[i]) {
              if (j in int64values) {
                response[i][j] = parseInt(response[i][j].toString(), 10);
              }
            }
          }
        }
        self.socketResponse(request, error, response);
      });
    });
  });
};

/**
 * Binds individual expressjs routes. Any additional routes should be added here.
 */
WebAppServer.prototype.bindRoutes = function() {
  var self = this;
  // Check to see if config is set.
  if(!this.configSet) {
    this.logger.info("Configuration file not set ", this.config);
    return false;
  }
  /**
   * Upload an Application archive.
   */
  this.app.post('/upload/:file', function (req, res) {
    var accountID = 'developer';
    self.Api.upload(accountID, req, res, req.params.file, self.socket);
  });

  /**
   * Check for new version.
   * http://www.continuuity.com/version
   */
  this.app.get('/version', function (req, res) {

    var options = {
      host: 'www.continuuity.com',
      path: '/version',
      port: '80'
    };

    res.set({
      'Content-Type': 'application-json'
    });

    http.request(options, function(response) {
      var data = '';
      response.on('data', function (chunk) {
        data += chunk;
      });

      response.on('end', function () {

        data = data.replace(/\n/g, '');

        res.send(JSON.stringify({
          current: self.VERSION,
          newest: data
        }));
        res.end();
      });
    }).end();

  });

  /**
   * Get a list of push destinations.
   */
  this.app.get('/destinations', function  (req, res) {

    fs.readFile(self.dirPath + '/.credential', 'utf-8', function (error, result) {

      res.on('error', function (e) {
        self.logger.trace('/destinations', e);
      });

      if (error) {

        res.write('false');
        res.end();

      } else {

        var options = {
          host: self.config['accounts-host'],
          path: '/api/vpc/list/' + result,
          port: self.config['accounts-port']
        };

        var request = https.request(options, function(response) {
          var data = '';
          response.on('data', function (chunk) {
            data += chunk;
          });

          response.on('end', function () {
            res.write(data);
            res.end();
          });

          response.on('error', function () {
            res.write('network');
            res.end();
          });
        });

        request.on('error', function () {
          res.write('network');
          res.end();
        });

        request.on('socket', function (socket) {
          socket.setTimeout(10000);
          socket.on('timeout', function() {

            request.abort();
            res.write('network');
            res.end();

          });
        });
        request.end();
      }
    });
  });

  /**
   * Save a credential / API Key.
   */
  this.app.post('/credential', function (req, res) {

    var apiKey = req.body.apiKey;

    // Write credentials to file.
    fs.writeFile(self.dirPath + '/.credential', apiKey,
      function (error, result) {
        if (error) {

          self.logger.warn('Could not write to ./.credential', error, result);
          res.write('Error: Could not write credentials file.');
          res.end();

        } else {
          self.Api.credential = apiKey;

          res.write('true');
          res.end();

        }
    });
  });

  /**
   * Catch port binding errors.
   */
  this.app.on('error', function () {
    self.logger.warn('Port ' + self.config['node-port'] + ' is in use.');
    process.exit(1);
  });
};

/**
 * Gets the local host.
 * @return {string} localhost ip address.
 */
WebAppServer.prototype.getLocalHost = function() {
  var os = require('os');
  var ifaces = os.networkInterfaces();
  var localhost = '';

  for (var dev in ifaces) {
    for (var i = 0, len = ifaces[dev].length; i < len; i++) {
      var details = ifaces[dev][i];
      if (details.family === 'IPv4') {
        if (dev === 'lo0') {
          localhost = details.address;
          break;
        }
      }
    }
  }
  return localhost;
};

/**
 * Export app.
 */
module.exports = WebAppServer;



