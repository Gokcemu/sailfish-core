/******************************************************************************
 * Copyright 2009-2021 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.exactpro.sf.storage.impl;

import java.time.Instant;
import java.util.Objects;

import com.exactpro.sf.common.messages.IMessage;
import com.exactpro.sf.common.util.EvolutionBatch;
import com.exactpro.sf.storage.IMessageStorage;
import com.exactpro.sf.storage.ScriptRun;

public class EvolutionMessageStorageWrapper extends MessageStorageWrapper {
    public EvolutionMessageStorageWrapper(IMessageStorage messageStorage) {
        super(Objects.requireNonNull(messageStorage, "Message storage can`t be null"));
    }

    @Override
    public void storeMessage(IMessage message) {
        if (EvolutionBatch.MESSAGE_NAME.equals(message.getName())) {
            EvolutionBatch batch = new EvolutionBatch(message);
            batch.getBatch().forEach(messageStorage::storeMessage);
            return;
        }
        messageStorage.storeMessage(message);
    }

    @Override
    public ScriptRun openScriptRun(String name, String description) {
        return messageStorage.openScriptRun(name, description);
    }

    @Override
    public void closeScriptRun(ScriptRun scriptRun) {
        messageStorage.closeScriptRun(scriptRun);

    }

    @Override
    public void removeMessages(Instant olderThan) {
        messageStorage.removeMessages(olderThan);
    }

    @Override
    public void removeMessages(String serviceID) {
        messageStorage.removeMessages(serviceID);
    }
}
