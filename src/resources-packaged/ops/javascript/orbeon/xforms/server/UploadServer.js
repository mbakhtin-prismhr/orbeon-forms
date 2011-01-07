/**
 * Copyright (C) 2011 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
(function() {

    /**
     * Upload server is a singleton.
     */
    ORBEON.xforms.server.UploadServer = {};

    var ExecutionQueue = ORBEON.util.ExecutionQueue;
    var UploadServer = ORBEON.xforms.server.UploadServer;
    var AjaxServer = ORBEON.xforms.server.AjaxServer;
    var Connect = YAHOO.util.Connect;
    var YD = YAHOO.util.Dom;

    // While an upload is in progress, the YUI object for that connection
    UploadServer.yuiConnection = null;
    // While an upload is in progress, the event for the field being uploaded
    UploadServer.processingEvent = null;
    // While an upload is in progress, the events for the fields that are left to be uploaded
    UploadServer.remainingEvents = [];
    // While an upload is in progress, call-back function when we are done processing all the events
    UploadServer.executionQueueDone = null;

    /**
     * Method called by YUI when the upload ends successfully.
     *
     * @private
     */
    UploadServer.uploadSuccess = function(o) {
        // Clear upload field we just uploaded, otherwise subsequent uploads will upload the same data again
        this.processingEvent.upload.clear();
        // The Ajax response typically contains information about each file (name, size, etc)
        AjaxServer.handleResponseAjax(o);
        // Are we done, or do we still need to handle events for other forms?
        this.continueWithRemainingEvents();
    };

    /**
     * In case of failure, after a delay call this.asyncUploadRequest() as it was called originally by the
     * execution queue.
     */
    UploadServer.uploadFailure = function() {
        console.log("Failure");
        AjaxServer.retryRequestAfterDelay(_.bind(function() {
            this.remainingEvents.unshift(this.processingEvent);
            this.asyncUploadRequest(this.remainingEvents, this.executionQueueDone);
        }, this));
    };

    /**
     * Once we are done processing the events (either because the uploads have been completed or canceled), handle the
     * remaining events.
     */
    UploadServer.continueWithRemainingEvents = function() {
        if (this.remainingEvents.length == 0) this.executionQueueDone();
        else UploadServer.asyncUploadRequest(this.remainingEvents, this.executionQueueDone);
    };

    /**
     * Run background form post do to the upload. This method is called by the ExecutionQueue when it determines that
     * the upload can be done.
     *
     * @private
     */
    UploadServer.asyncUploadRequest = function(events, done) {
        this.executionQueueDone = done;
        this.processingEvent = events[0];
        this.remainingEvents = _.tail(events);
        // Switch the upload to progress state, so users can't change the file and know the upload is in progress
        this.processingEvent.upload.setState("progress");
        // Tell server we're starting uploads
        AjaxServer.fireEvents([new AjaxServer.Event(null, this.processingEvent.upload.container.id, null, null, "xxforms-upload-start")], false);
        // Disabling fields other than the one we want to upload
        var disabledElements = _.filter(this.processingEvent.form.elements, function(element) {
            // Keep in form post the $uuid element and input for this upload
            var keep = element.name == "$uuid" || (YD.hasClass(element, "xforms-upload-select")
                    && element.name == this.processingEvent.upload.container.id);
            // Disable elements we don't keep and that are not disabled already
            if (! keep && ! element.disabled) { element.disabled = true; return true; }
            else return false;
        }, this);
        // Trigger actual upload through a form POST
        Connect.setForm(this.processingEvent.form, true, true);
        this.yuiConnection = Connect.asyncRequest("POST", ORBEON.xforms.Globals.xformsServerURL[this.processingEvent.form.id],
            { upload: _.bind(this.uploadSuccess, this), failure: _.bind(this.uploadFailure, this) });
        // Enable the controls we previously disabled
        _.each(disabledElements, function(element) { element.disabled = false; });
    };

    /**
     * Cancels the uploads currently in process. This is called by the control, which delegates canceling to the
     * UploadServer as it can't know about other controls being "uploaded" at the same time. Indeed, we can have
     * uploads for multiple files at the same time, and for each one of the them, we want to clear the upload field,
     * and switch back to the empty state so users can again select a file to upload.
     */
    UploadServer.cancel = function() {
        Connect.abort(UploadServer.yuiConnection);
        this.processingEvent.upload.clear();
        this.processingEvent.upload.setState("empty");
        AjaxServer.fireEvents([new AjaxServer.Event(null, this.processingEvent.upload.container.id, null, null, "xxforms-upload-cancel")], false);
        this.continueWithRemainingEvents();
    };

    /**
     * Queue for upload events.
     * @private
     */
    UploadServer.uploadEventQueue = new ExecutionQueue(_.bind(UploadServer.asyncUploadRequest, UploadServer));
})();