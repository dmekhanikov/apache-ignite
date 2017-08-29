/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

'use strict';

const express = require('express');

// Fire me up!

module.exports = {
    implements: 'routes/invites',
    inject: ['services/mails', 'services/invites']
};

/**
 * @param {MailsService} mailsService
 * @param {InvitesService} invitesService
 * @returns {Promise}
 */
module.exports.factory = function(mailsService, invitesService) {
    return new Promise((resolveFactory) => {
        const router = new express.Router();

        // Invite user to join organization.
        router.put('/', (req, res) => {
            invitesService.create(req.user, req.body.email)
                .then((invite) => mailsService.emailInvite(req.origin(), req.user, invite))
                .then(res.api.ok)
                .catch(res.api.error);
        });

        resolveFactory(router);
    });
};
