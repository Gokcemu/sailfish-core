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

import * as React from 'react';
import VerificationEntry from "../../models/VerificationEntry";
import { StatusType } from "../../models/Status";
import "../../styles/tables.scss";
import { connect } from 'react-redux';
import AppState from "../../state/models/AppState";
import { createSelector } from '../../helpers/styleCreators';
import StateSaver, { RecoverableElementProps } from "./../util/StateSaver";
import SearchableContent from '../search/SearchableContent';
import { getVerificationExpandPath } from '../../helpers/search/getExpandPath';
import SearchResult from '../../helpers/search/SearchResult';
import { replaceNonPrintableChars } from '../../helpers/stringUtils';
import { copyTextToClipboard } from '../../helpers/copyHandler';

const PADDING_LEVEL_VALUE = 15;

interface OwnProps {
    actionId: number;
    messageId: number;
    params: VerificationEntry[];
    status: StatusType;
    keyPrefix: string;
    onExpand: () => void;
}

interface StateProps {
    transparencyFilter: Set<StatusType>;
    visibilityFilter: Set<StatusType>;
    expandPath: number[];
    searchResults: SearchResult;
}

interface Props extends Omit<OwnProps, 'params'>, StateProps {
    nodes: TableNode[];
    stateSaver: (state: TableNode[]) => void;
}

interface RecoveredProps extends OwnProps, RecoverableElementProps, StateProps {}

interface State {
    nodes: TableNode[];
}

interface TableNode extends VerificationEntry {
    //is subnodes visible
    isExpanded?: boolean;
}

class VerificationTableBase extends React.Component<Props, State> {

    constructor(props: Props) {
        super(props);
        this.state = {
            nodes: props.nodes
        }
    }

    findNode(node: TableNode, targetNode: TableNode): TableNode {
        if (node === targetNode) {
            return {
                ...targetNode,
                isExpanded: !targetNode.isExpanded
            };
        }

        return {
            ...node,
            subEntries: node.subEntries && node.subEntries.map(subNode => this.findNode(subNode, targetNode))
        };
    }

    setExpandStatus(isCollapsed: boolean) {
        this.setState({
            nodes: this.state.nodes.map(
                node => node.subEntries ? this.setNodeExpandStatus(node, isCollapsed) : node)
        });
    }

    setNodeExpandStatus(node: TableNode, isExpanded: boolean): TableNode {
        return {
            ...node,
            isExpanded: isExpanded,
            subEntries: node.subEntries && node.subEntries.map(
                subNode => subNode.subEntries ? this.setNodeExpandStatus(subNode, isExpanded) : subNode
            )
        }
    }

    componentDidUpdate(prevProps: Props, prevState: State) {
        // handle expand state changing to remeasure card size
        if (this.state.nodes !== prevState.nodes || this.props.visibilityFilter !== prevProps.visibilityFilter) {
            this.props.onExpand();
        }

        if (this.props.expandPath !== prevProps.expandPath && this.props.expandPath.length > 0) {
            this.setState({
                nodes: this.updateExpandPath(this.props.expandPath, this.state.nodes)
            });
        }
    }

    componentWillUnmount() {
        this.props.stateSaver(this.state.nodes);
    }

    updateExpandPath([currentIndex, ...expandPath]: number[], prevState: TableNode[]): TableNode[] {
        return prevState.map(
            (node, index): TableNode => index === currentIndex ? {
                ...node,
                isExpanded: true,
                subEntries: node.subEntries && this.updateExpandPath(expandPath, node.subEntries)
            } : node
        )
    }

    render() {
        const { status, keyPrefix } = this.props,
            { nodes } = this.state;

        const rootClass = createSelector("ver-table", status);

        return (
            <div className={rootClass}>
                <div className="ver-table-header">
                    <div className="ver-table-header-name">
                        <h5>Comparison Table</h5>
                    </div>
                    <div className="ver-table-header-control">
                        <span className="ver-table-header-control-button"
                            onClick={this.onControlButtonClick(false)}>
                            Collapse
                        </span>
                        <span> | </span>
                        <span className="ver-table-header-control-button"
                            onClick={this.onControlButtonClick(true)}>
                            Expand
                        </span>
                        <span> all groups</span>
                    </div>
                </div>
                <table>
                    <thead>
                        <tr>
                            <th>Name</th>
                            <th className="ver-table-flexible">Expected</th>
                            <th className="ver-table-flexible">Actual</th>
                            <th>Status</th>
                        </tr>
                    </thead>
                    <tbody>
                        {nodes.map((param, index) => this.renderTableNodes(param, `${keyPrefix}-${index}`))}
                    </tbody>
                </table>
            </div>
        )
    }

    private renderTableNodes(node: TableNode, key: string, paddingLevel: number = 1) : React.ReactNodeArray {
        if (node.status != null && !this.props.visibilityFilter.has(node.status)) {
            return [];
        }

        if (node.subEntries) {

            const subNodes = node.isExpanded ? 
                node.subEntries.reduce(
                    (lsit, node, index) => lsit.concat(this.renderTableNodes(node, `${key}-${index}`, paddingLevel + 1)), []
                ) : [];

            return [this.renderNode(node, paddingLevel, key), ...subNodes];
        } else {
            return [this.renderNode(node, paddingLevel, key)];
        }
    }

    private renderNode(node: TableNode, paddingLevel: number, key: string): React.ReactNode {
        const { transparencyFilter } = this.props,
            { name, expected, actual, status, isExpanded, subEntries } = node;

        const isToggler = subEntries != null && subEntries.length > 0,
            isTransparent = status != null && !transparencyFilter.has(status),
            expectedReplaced = replaceNonPrintableChars(expected),
            actualReplaced = replaceNonPrintableChars(actual);

        const rootClassName = createSelector(
                "ver-table-row",
                isTransparent ? "transparent" : null
            ),
            statusClassName = createSelector(
                "ver-table-row-status", 
                status
            ),
            togglerClassName = createSelector(
                "ver-table-row-toggler",
                isExpanded ? "expanded" : "collapsed"
            );

        return (
            <tr className={rootClassName} key={key}>
                {
                    isToggler ? (
                        <td className={togglerClassName}
                            onClick={this.onTogglerClick(node)}>
                            <p style={{ marginLeft: PADDING_LEVEL_VALUE * (paddingLevel - 1) }}>
                                {this.renderContent(`${key}-name`, name)}
                            </p>
                            <span className="ver-table-row-count">{subEntries.length}</span>
                        </td>
                    ) : (
                        <td style={{ paddingLeft: PADDING_LEVEL_VALUE * paddingLevel }}>
                            {this.renderContent(`${key}-name`, name)}
                        </td>
                    )
                }
                <td className="ver-table-row-expected" onCopy={this.onCopyFor(expected)}>
                    {this.renderContent(`${key}-expected`, expected, expectedReplaced)}
                </td>
                <td className="ver-table-row-actual" onCopy={this.onCopyFor(actual)}>
                    {this.renderContent(`${key}-actual`, actual, actualReplaced)}                  
                </td>
                <td className={statusClassName}>
                    {this.renderContent(`${key}-status`, status)}
                </td>
            </tr>
        );
    }

    /**
     * We need this for optimization - render SearchableContent component only if it contains some search results
     * @param contentKey for SearchableContetn component 
     * @param content 
     * @param fakeContent This text will be rendered when there is no search results found - 
     *     it's needed to render fake dots and squares instead of real non-printable characters
     */
    private renderContent(contentKey: string, content: string, fakeContent: string = content): React.ReactNode {
        if (!this.props.searchResults.isEmpty && this.props.searchResults.get(contentKey)) {
            return (
                <SearchableContent
                    contentKey={contentKey}
                    content={content}/>
            )
        } else {
            return fakeContent;
        }
    }

    private onCopyFor = (realText: string) => (e: React.ClipboardEvent<HTMLDivElement>) => {
        const selectionRange = window.getSelection().getRangeAt(0),
            copiedText = realText.substring(selectionRange.startOffset, selectionRange.endOffset);

        e.preventDefault();
        copyTextToClipboard(copiedText);
    }

    private onTogglerClick = (targetNode: TableNode) => (e: React.MouseEvent) => {
        this.setState({
            ...this.state,
            nodes: this.state.nodes.map(node => this.findNode(node, targetNode))
        });

        e.stopPropagation();
    }

    private onControlButtonClick = (expandStatus: boolean) => (e: React.MouseEvent) => {
        this.setExpandStatus(expandStatus);
        e.stopPropagation();
    }
}


export const RecoverableVerificationTable = ({ stateKey, ...props }: RecoveredProps) => (
    // at first table render, we need to generate table nodes if we don't find previous table's state 
    <StateSaver
        stateKey={stateKey}
        getDefaultState={() => props.params ? props.params.map(param => paramsToNodes(param)) : []}>
        {
            (state: TableNode[], stateHandler) => (
                <VerificationTableBase
                    {...props}
                    nodes={state}
                    stateSaver={stateHandler}/>
            )
        }
    </StateSaver>
)

function paramsToNodes(root: VerificationEntry): TableNode {
    return root.subEntries ? {
        ...root,
        subEntries: root.subEntries.map((param) => paramsToNodes(param)),
        isExpanded: true
    } : root;
}

export const VerificationTable = connect(
    (state: AppState, ownProps: OwnProps): StateProps => ({
        transparencyFilter: state.filter.fieldsTransparencyFilter,
        visibilityFilter: state.filter.fieldsFilter,
        expandPath: getVerificationExpandPath(state.selected.searchResults, state.selected.searchIndex, ownProps.actionId, ownProps.messageId),
        searchResults: state.selected.searchResults
    }),
    dispatch => ({})
)(RecoverableVerificationTable);
