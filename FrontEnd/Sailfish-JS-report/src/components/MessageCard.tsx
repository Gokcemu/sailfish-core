/******************************************************************************
 * Copyright 2009-2019 Exactpro (Exactpro Systems Limited)
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

import { h, Component } from 'preact';
import Message from '../models/Message';
import { StatusType } from '../models/Status';
import Action from '../models/Action';
import { MessageRaw } from './MessageRaw';
import { getHashCode } from '../helpers/stringHash';
import { formatTime } from '../helpers/dateFormatter';
import { MessageCardActionChips } from './MessageCardActionChips';
import { MlUploadButton } from './MlUploadButton';
import '../styles/messages.scss';
import { createSelector, createBemElement } from '../helpers/styleCreators';
import { createBemBlock } from '../helpers/styleCreators';
import { connect } from 'preact-redux';
import AppState from '../state/models/AppState';
import { selectMessage } from '../actions/actionCreators';

const HUE_SEGMENTS_COUNT = 36;

export interface MessageCardOwnProps {
    message: Message;
    actions: Action[];
    rejectedMessagesCount?: number;
    showRaw?: boolean;
    showRawHandler?: (showRaw: boolean) => any;
}

interface MessageCardStateProps {
    isSelected?: boolean;
    status?: StatusType;
}

interface MessageCardDispatchProps {
    selectHandler: (status?: StatusType) => any;
}

export interface MessageCardProps extends MessageCardOwnProps, MessageCardStateProps, MessageCardDispatchProps { }

const MessageCardBase = ({ message, isSelected, actions, status, rejectedMessagesCount, selectHandler, showRaw, showRawHandler }: MessageCardProps) => {
    const { msgName, timestamp, from, to, contentHumanReadable, raw } = message;

    const rejectedTitle = message.content.rejectReason,
        labelsCount = getLabelsCount(message);

    const rootClass = createBemBlock("message-card", status, isSelected ? "selected" : null), 
        showRawClass = createBemElement("mc-show-raw", "icon", showRaw ? "expanded" : "hidden"),
        // session arrow color, we calculating it for each session from-to pair, based on hash 
        sessionArrowStyle = { filter: `invert(1) sepia(1) saturate(5) hue-rotate(${calculateHueValue(from, to)}deg)` };

    return (
        <div class={rootClass}>
            <div class="message-card__labels">
                {renderMessageTypeLabels(message)}
            </div>
                <div class="mc-header default" 
                    data-lb-count={labelsCount}
                    onClick={() => selectHandler()}>
                    {
                        rejectedMessagesCount && actions.length == 0 ?
                            (
                                <div class="mc-header__info rejected">
                                    <p>Rejected {rejectedMessagesCount}</p>
                                </div>
                            )
                            : (
                                <MessageCardActionChips
                                    actions={actions}
                                    selectedStatus={status}
                                    selectHandler={selectHandler} />
                            )
                    }
                    <div class="mc-header__name">
                        <span>Name</span>
                    </div>
                    <div class="mc-header__name-value">
                        <p>{msgName}</p>
                    </div>
                    <div class="mc-header__timestamp">
                        <p>{formatTime(timestamp)}</p>
                    </div>
                    <div class="mc-header__session">
                        <span>Session</span>
                    </div>
                    <div class="mc-header__from">
                        <p>{from}</p>
                    </div>
                    {
                        from && to ?
                            <div class="mc-header__session-icon"
                                style={sessionArrowStyle} />
                            : null
                    }
                    <div class="mc-header__to">
                        <p>{to}</p>
                    </div>
                    <MlUploadButton messageId={message.id}/>
                </div>
                <div class="message-card__body   mc-body">
                    {
                        (message.content.rejectReason !== null) ?
                            (
                                <div class="mc-body__title">
                                    <p>{rejectedTitle}</p>
                                </div>
                            )
                            : null
                    }
                    <div class="mc-body__human">
                        <p>
                            {contentHumanReadable}
                            {
                                (raw && raw !== 'null') ? (
                                    <div class="mc-show-raw"
                                        onClick={e => showRawHandler && showRawHandler(!showRaw)}>
                                        <div class="mc-show-raw__title">RAW</div>
                                        <div class={showRawClass} />
                                    </div>
                                ) : null
                            }
                        </p>
                    </div>
                    {
                        showRaw ?
                            <MessageRaw
                                rawContent={raw} />
                            : null
                    }
                </div>
            </div>
    );
}

function renderMessageTypeLabels(message: Message): JSX.Element[] {
    let labels = [];

    if (message.content.rejectReason !== null) {
        labels.push(
            <div class="mc-label rejected">
                <div class="mc-label__icon rejected" />
            </div>
        );
    }

    if (message.content.admin) {
        labels.push(
            <div class="mc-label admin">
                <div class="mc-label__icon admin" />
            </div>
        )
    }

    return labels;
}

function getLabelsCount(message: Message) {
    let count = 0;

    if (message.content.rejectReason != null) {
        count++;
    }

    if (message.content.admin) {
        count++;
    }

    return count;
}

function calculateHueValue(from: string, to: string): number {
    return (getHashCode([from, to].filter(str => str).sort((a, b) => a.localeCompare(b)).join(''))
        % HUE_SEGMENTS_COUNT) * (360 / HUE_SEGMENTS_COUNT);
}

export const MessageCardContainer = connect(
    (state: AppState, ownProps: MessageCardOwnProps): MessageCardStateProps => ({
        isSelected: state.selected.messagesId.includes(ownProps.message.id) || state.selected.rejectedMessageId === ownProps.message.id,
        status: state.selected.messagesId.includes(ownProps.message.id) ? state.selected.status : null
    }),
    (dispatch, ownProps: MessageCardOwnProps) : MessageCardDispatchProps => ({
        selectHandler: (status?: StatusType) => dispatch(selectMessage(ownProps.message, status))
    })
);

export const MessageCard = MessageCardContainer(MessageCardBase);
