/******************************************************************************
 * Copyright 2009-2018 Exactpro (Exactpro Systems Limited)
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
package com.exactpro.sf.comparison;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.mina.filter.codec.ProtocolCodecException;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.exactpro.sf.aml.script.ActionContext;
import com.exactpro.sf.aml.script.MetaContainer;
import com.exactpro.sf.aml.scriptutil.StaticUtil;
import com.exactpro.sf.aml.scriptutil.StaticUtil.IFilter;
import com.exactpro.sf.common.impl.messages.DefaultMessageFactory;
import com.exactpro.sf.common.impl.messages.MapMessage;
import com.exactpro.sf.common.messages.IMessage;
import com.exactpro.sf.common.messages.IMessageFactory;
import com.exactpro.sf.common.messages.structures.IDictionaryStructure;
import com.exactpro.sf.common.messages.structures.loaders.IDictionaryStructureLoader;
import com.exactpro.sf.common.messages.structures.loaders.XmlDictionaryStructureLoader;
import com.exactpro.sf.scriptrunner.StatusType;
import com.exactpro.sf.util.AbstractTest;
import com.exactpro.sf.util.DateTimeUtility;

@SuppressWarnings("deprecation")
public class TestMessageComparator extends AbstractTest {

    private static final Set<String> uncheckedFields = new HashSet<>();

    static {
        uncheckedFields.add("BeginString");
        uncheckedFields.add("BodyLength");
        uncheckedFields.add("MsgSeqNum");
        uncheckedFields.add("MsgType");
        uncheckedFields.add("SenderCompID");
        uncheckedFields.add("TargetCompID");
        uncheckedFields.add("PosDupFlag");
        uncheckedFields.add("OrigSendingTime");
        uncheckedFields.add("SendingTime");
        uncheckedFields.add("CheckSum");
        uncheckedFields.add("templateId");
        uncheckedFields.add("ApplVerID");
        uncheckedFields.add("SenderSubID");
    }

	@Test
    public void testNullAndNotNull() {

        IMessage message = messageFactory.createMessage("name", "namespace");
        IMessage filter = messageFactory.createMessage("name", "namespace");

        IFilter nullFilter = StaticUtil.nullFilter(0, null);
        IFilter notNullFilter = StaticUtil.notNullFilter(0, null);
        IFilter missedFilter = StaticUtil.simpleFilter(0, null, "com.exactpro.sf.comparison.Convention.CONV_MISSED_OBJECT");
        IFilter presentFilter = StaticUtil.simpleFilter(0, null, "com.exactpro.sf.comparison.Convention.CONV_PRESENT_OBJECT");

        message.addField("ExplicitNull_NullFilter", null);
        filter.addField("ExplicitNull_NullFilter", nullFilter);
        filter.addField("HiddenNull_NullFilter", nullFilter);

        message.addField("ExplicitNull_EmptyValue", null);
        filter.addField("ExplicitNull_EmptyValue", missedFilter);
        filter.addField("HiddenNull_EmptyValue", missedFilter);

        message.addField("ExplicitNull_NotNullFilter", null);
        filter.addField("ExplicitNull_NotNullFilter", notNullFilter);
        filter.addField("HiddenNull_NotNullFilter", notNullFilter);

        message.addField("ExplicitNull_AnyValue", null);
        filter.addField("ExplicitNull_AnyValue", presentFilter);
        filter.addField("HiddenNull_AnyValue", presentFilter);

        ComparatorSettings compareSettings = new ComparatorSettings();

        ComparisonResult comparisonResult = MessageComparator.compare(message, filter, compareSettings);

        validateResult(comparisonResult, 4, 4, 0);
    }

	@Test
	public void testPrecision() {

		String failed = StatusType.FAILED.name();
		String passed = StatusType.PASSED.name();

        IMessage message = messageFactory.createMessage("name", "namespace");
        IMessage filter = messageFactory.createMessage("name", "namespace");

		message.addField("float" + failed, 1.001f);
		filter.addField("float" + failed, 1.002f);

		message.addField("floatDou" + passed, 1.001f);
		filter.addField("floatDou" + passed, 1.002f);

        message.addField("floatSys" + passed, 1.001f);
        filter.addField("floatSys" + passed, 1.001f);

		message.addField("double" + failed, 1.001d);
		filter.addField("double" + failed, 1.002d);

		message.addField("doubleDou" + passed, 1.001d);
		filter.addField("doubleDou" + passed, 1.002d);

		message.addField("doubleSys" + passed, 1.001d);
		filter.addField("doubleSys" + passed, 1.001d);

		message.addField("bigDecimal" + failed, new BigDecimal("1.001"));
		filter.addField("bigDecimal" + failed, new BigDecimal("1.002"));

		message.addField("bigDecimalDou" + passed, new BigDecimal("1.001"));
		filter.addField("bigDecimalDou" + passed, new BigDecimal("1.002"));

		message.addField("bigDecimalSys" + passed, new BigDecimal("1.001"));
		filter.addField("bigDecimalSys" + passed, new BigDecimal("1.001"));

		MetaContainer metaContainer = new MetaContainer();
		metaContainer.addDoublePrecision("floatDou" + passed + "=0.01");
		metaContainer.addSystemPrecision("floatSys" + failed + "=0.00025");
		metaContainer.addDoublePrecision("doubleDou" + passed + "=0.01");
		metaContainer.addSystemPrecision("doubleSys" + passed + "=0.00025");
		metaContainer.addDoublePrecision("bigDecimalDou" + passed + "=0.01");
		metaContainer.addSystemPrecision("bigDecimalSys" + passed + "=0.00025");

        ComparatorSettings compareSettings = new ComparatorSettings();
		compareSettings.setMetaContainer(metaContainer);

        ComparisonResult comparisonResult = MessageComparator.compare(message, filter, compareSettings);

        for(ComparisonResult subResult : comparisonResult) {
			if (subResult.getName().endsWith(passed)) {
				Assert.assertEquals(subResult.getName(), StatusType.PASSED, subResult.getStatus());
			} else if (subResult.getName().endsWith(failed)) {
				Assert.assertEquals(subResult.getName(), StatusType.FAILED, subResult.getStatus());
			} else {
				Assert.fail("Unknown comparison " + subResult.getName());
			}
		}
	}

	private IMessageFactory messageFactory;

	@Before
	public void init() {
		this.messageFactory = DefaultMessageFactory.getFactory();
	}

	@Test
	public void testFailUnexpected_N()
	{
		// Fail + 3 {BodyLength, MsgSeqNum, CheckSum}; NA - 3 {BodyLength, MsgSeqNum, CheckSum};
		validateResult(doUnexpectedCompareIMessage("N", 0, 0, FilterType.VALUE), 2, 1+3, 5-3);
		// Fail + 3 {BodyLength, MsgSeqNum, CheckSum}; NA - 4 {BodyLength, MsgSeqNum, CheckSum, NoPartyIDs(Count)};
        validateResult(doUnexpectedCompareIMessage("N", 0, 1, FilterType.VALUE), 2, 1 + 3, 10 - 4);
        validateResult(doUnexpectedCompareIMessage("N", 0, 2, FilterType.VALUE), 2, 1 + 3, 14 - 4);
        validateResult(doUnexpectedCompareIMessage(null, 1, 0, FilterType.VALUE), 2, 2 + 3, 8 - 4);
        validateResult(doUnexpectedCompareIMessage(null, 1, 1, FilterType.VALUE), 4, 1+3, 7-4);
        validateResult(doUnexpectedCompareIMessage(null, 1, 2, FilterType.VALUE), 4, 1 + 3, 11 - 4);
        validateResult(doUnexpectedCompareIMessage("", 2, 0, FilterType.VALUE), 2, 3 + 3, 10 - 4);
        validateResult(doUnexpectedCompareIMessage("", 2, 1, FilterType.VALUE), 4, 2 + 3, 9 - 4);
        validateResult(doUnexpectedCompareIMessage("", 2, 2, FilterType.VALUE), 6, 1+3, 8-4);

        // Pass + 1 {1 * group_NoPartyIDs} Fail + 3 {BodyLength, MsgSeqNum, CheckSum}; NA - 3 {BodyLength, MsgSeqNum, CheckSum};
        validateResult(doUnexpectedCompareIMessage("N", 0, 0, FilterType.NOT_EMPTY), 2+1*1, 1+3, 5-3);
        // Pass + 1 {1 * group_NoPartyIDs}; Fail + 3 {BodyLength, MsgSeqNum, CheckSum}; NA - 4 {BodyLength, MsgSeqNum, CheckSum, NoPartyIDs(Count)};
        validateResult(doUnexpectedCompareIMessage("N", 0, 1, FilterType.NOT_EMPTY), 2 + 1 * 1, 1 + 3, 10 - 4);
        // Pass + 1 {1 * group_NoPartyIDs}; Fail + 3 {BodyLength, MsgSeqNum, CheckSum}; NA - 11 {BodyLength, MsgSeqNum, CheckSum, NoPartyIDs(Count)};
        validateResult(doUnexpectedCompareIMessage("N", 0, 2, FilterType.NOT_EMPTY), 2 + 1, 1 + 3, 14 - 4);

        // Fail + 4 {BodyLength, MsgSeqNum, CheckSum, 1 * group_NoPartyIDs}; NA - 3 {BodyLength, MsgSeqNum, CheckSum};
        validateResult(doUnexpectedCompareIMessage("N", 0, 0, FilterType.EMPTY), 2, 1+3+1*1, 5-3);
        // Fail + 4 {BodyLength, MsgSeqNum, CheckSum, 1 * group_NoPartyIDs}; NA - 4 {BodyLength, MsgSeqNum, CheckSum, NoPartyIDs(Count)};
        validateResult(doUnexpectedCompareIMessage("N", 0, 1, FilterType.EMPTY), 2, 1 + 3 + 1 * 1, 10 - 4);
        // Fail + 5 {BodyLength, MsgSeqNum, CheckSum, 2 * group_NoPartyIDs}; NA - 4 {BodyLength, MsgSeqNum, CheckSum, NoPartyIDs(Count)};
        validateResult(doUnexpectedCompareIMessage("N", 0, 2, FilterType.EMPTY), 2, 1 + 3 + 1, 14 - 4);
	}

    @Test
	public void testFailUnexpected_Y()
	{
        // Fail + 3 {BodyLength, MsgSeqNum, CheckSum}; NA - 3 {BodyLength, MsgSeqNum, CheckSum};
		validateResult(doUnexpectedCompareIMessage("Y", 0, 0, FilterType.VALUE), 2, 2+3, 4-3);
        // Fail + 3 {BodyLength, MsgSeqNum, CheckSum}; NA - 4 {BodyLength, MsgSeqNum, CheckSum, NoPartyIDs(Count)};
        validateResult(doUnexpectedCompareIMessage("Y", 0, 1, FilterType.VALUE), 2, 2 + 3, 9 - 4);
        validateResult(doUnexpectedCompareIMessage("Y", 0, 2, FilterType.VALUE), 2, 2 + 3, 13 - 4);
        validateResult(doUnexpectedCompareIMessage("Y", 1, 0, FilterType.VALUE), 2, 3 + 3, 7 - 4);
		validateResult(doUnexpectedCompareIMessage("Y", 1, 1, FilterType.VALUE), 4, 3+3, 5-4);
        validateResult(doUnexpectedCompareIMessage("Y", 1, 2, FilterType.VALUE), 4, 3 + 3, 9 - 4);
        validateResult(doUnexpectedCompareIMessage("Y", 2, 0, FilterType.VALUE), 2, 4 + 3, 9 - 4);
        validateResult(doUnexpectedCompareIMessage("Y", 2, 1, FilterType.VALUE), 4, 4 + 3, 7 - 4);
		validateResult(doUnexpectedCompareIMessage("Y", 2, 2, FilterType.VALUE), 6, 4+3, 5-4);

		// Pass + 1 {1 * group_NoPartyIDs} Fail + 3 {BodyLength, MsgSeqNum, CheckSum}; NA - 3 {BodyLength, MsgSeqNum, CheckSum};
        validateResult(doUnexpectedCompareIMessage("Y", 0, 0, FilterType.NOT_EMPTY), 2+1*1, 2+3, 4-3);
        // Pass + 1 {1 * group_NoPartyIDs}; Fail + 3 {BodyLength, MsgSeqNum, CheckSum}; NA - 4 {BodyLength, MsgSeqNum, CheckSum, NoPartyIDs(Count)};
        validateResult(doUnexpectedCompareIMessage("Y", 0, 1, FilterType.NOT_EMPTY), 2 + 1 * 1, 2 + 3, 9 - 4);
        // Pass + 1 {1 * group_NoPartyIDs}; Fail + 3 {BodyLength, MsgSeqNum, CheckSum}; NA - 11 {BodyLength, MsgSeqNum, CheckSum, NoPartyIDs(Count)};
        validateResult(doUnexpectedCompareIMessage("Y", 0, 2, FilterType.NOT_EMPTY), 2 + 1, 2 + 3, 13 - 4);

        // Fail + 4 {BodyLength, MsgSeqNum, CheckSum, 1 * group_NoPartyIDs}; NA - 3 {BodyLength, MsgSeqNum, CheckSum};
        validateResult(doUnexpectedCompareIMessage("Y", 0, 0, FilterType.EMPTY), 2, 2+3+1*1, 4-3);
        // Fail + 4 {BodyLength, MsgSeqNum, CheckSum, 1 * group_NoPartyIDs}; NA - 4 {BodyLength, MsgSeqNum, CheckSum, NoPartyIDs(Count)};
        validateResult(doUnexpectedCompareIMessage("Y", 0, 1, FilterType.EMPTY), 2, 2 + 3 + 1 * 1, 9 - 4);
        // Fail + 5 {BodyLength, MsgSeqNum, CheckSum, 2 * group_NoPartyIDs}; NA - 4 {BodyLength, MsgSeqNum, CheckSum, NoPartyIDs(Count)};
        validateResult(doUnexpectedCompareIMessage("Y", 0, 2, FilterType.EMPTY), 2, 2 + 3 + 1, 13 - 4);
	}

	@Test
	public void testFailUnexpected_A()
	{
		// Fail + 3 {BodyLength, MsgSeqNum, CheckSum}; NA - 3 {BodyLength, MsgSeqNum, CheckSum};
		validateResult(doUnexpectedCompareIMessage("A", 0, 0, FilterType.VALUE), 2, 2+3, 4-3);
		// Fail + 3 {BodyLength, MsgSeqNum, CheckSum} - 1 {NoPartyIDs(Count)}; NA - 3 {BodyLength, MsgSeqNum, CheckSum};
        validateResult(doUnexpectedCompareIMessage("A", 0, 1, FilterType.VALUE), 2, 4 + 2, 7 - 3);
        validateResult(doUnexpectedCompareIMessage("A", 0, 2, FilterType.VALUE), 2, 5 + 2, 10 - 3);
	    // Fail + 3 {BodyLength, MsgSeqNum, CheckSum}; NA - 4 {BodyLength, MsgSeqNum, CheckSum, NoPartyIDs(Count)};
        validateResult(doUnexpectedCompareIMessage("A", 1, 0, FilterType.VALUE), 2, 3 + 3, 7 - 4);
		validateResult(doUnexpectedCompareIMessage("A", 1, 1, FilterType.VALUE), 4, 3+3, 5-4);
        validateResult(doUnexpectedCompareIMessage("A", 1, 2, FilterType.VALUE), 4, 4 + 3, 8 - 4);
        validateResult(doUnexpectedCompareIMessage("A", 2, 0, FilterType.VALUE), 2, 4 + 3, 9 - 4);
        validateResult(doUnexpectedCompareIMessage("A", 2, 1, FilterType.VALUE), 4, 4 + 3, 7 - 4);
		validateResult(doUnexpectedCompareIMessage("A", 2, 2, FilterType.VALUE), 6, 4+3, 5-4);

		// Pass + 1 {1 * group_NoPartyIDs} Fail + 3 {BodyLength, MsgSeqNum, CheckSum}; NA - 3 {BodyLength, MsgSeqNum, CheckSum};
        validateResult(doUnexpectedCompareIMessage("A", 0, 0, FilterType.NOT_EMPTY), 2+1*1, 2+3, 4-3);
        // Pass + 1 {1 * group_NoPartyIDs}; Fail + 3 {BodyLength, MsgSeqNum, CheckSum} - 4 {group_NoPartyIDs, 1 * Group(3)}; NA - 4 {BodyLength, MsgSeqNum, CheckSum, NoPartyIDs(Count)} + 4 {group_NoPartyIDs, 1 * Group(3)};
        validateResult(doUnexpectedCompareIMessage("A", 0, 1, FilterType.NOT_EMPTY), 2 + 1 * 1, 6 + 3 - 1 - 1 * 3, 4 - 4 + 1 + 1 * 4);
        // Pass + 1 {1 * group_NoPartyIDs}; Fail + 3 {BodyLength, MsgSeqNum, CheckSum} - 7 {group_NoPartyIDs, 2 * Group(3)}; NA - 11 {BodyLength, MsgSeqNum, CheckSum, NoPartyIDs(Count)} + 7 {group_NoPartyIDs, 2 * Group(3)};
        validateResult(doUnexpectedCompareIMessage("A", 0, 2, FilterType.NOT_EMPTY), 2 + 1, 9 + 3 - 1 - 2 * 3, 4 - 4 + 1 + 2 * 4);

        // Fail + 4 {BodyLength, MsgSeqNum, CheckSum, 1 * group_NoPartyIDs}; NA - 3 {BodyLength, MsgSeqNum, CheckSum};
        validateResult(doUnexpectedCompareIMessage("A", 0, 0, FilterType.EMPTY), 2, 2+3+1*1, 4-3);
        // Fail + 4 {BodyLength, MsgSeqNum, CheckSum, 1 * group_NoPartyIDs} - 4 {NoPartyIDs, 1 * Group(3)}; NA - 4 {BodyLength, MsgSeqNum, CheckSum, NoPartyIDs(Count)} + 4 {group_NoPartyIDs, 1 * Group(3)};
        validateResult(doUnexpectedCompareIMessage("A", 0, 1, FilterType.EMPTY), 2, 6 + 3 + 1 * 1 - 1 - 1 * 3, 4 - 4 + 1 + 1 * 4);
        // Fail + 5 {BodyLength, MsgSeqNum, CheckSum, 2 * group_NoPartyIDs} - 7 {NoPartyIDs, 2 * Group(3)}; NA - 4 {BodyLength, MsgSeqNum, CheckSum, NoPartyIDs(Count)} + 7 {group_NoPartyIDs, 2 * Group(3)};
        validateResult(doUnexpectedCompareIMessage("A", 0, 2, FilterType.EMPTY), 2, 9 + 3 + 1 * 1 - 1 - 2 * 3, 4 - 4 + 1 + 2 * 4);
	}

    private void validateResult(ComparisonResult comparatorResult, int passed, int failed, int na) {
//		System.out.println(ComparisonUtil.toTable(table));
        System.out.println(comparatorResult);
	    System.out.println();
        int apassed = ComparisonUtil.getResultCount(comparatorResult, StatusType.PASSED);
        int afailed = ComparisonUtil.getResultCount(comparatorResult, StatusType.FAILED);
        int ana = ComparisonUtil.getResultCount(comparatorResult, StatusType.NA);
		//System.out.println(apassed+", "+afailed+", "+ana);
		Assert.assertEquals("PASSED", passed, apassed);
		Assert.assertEquals("FAILED", failed, afailed);
		Assert.assertEquals("N/A", na, ana);
	}

	private ComparisonResult doUnexpectedCompareIMessage(String failUnexpected, int expected, int actual, FilterType filterType)
    {
        //System.out.println("------- failUnexpected="+failUnexpected+", expected="+expected+", actual="+actual);
	    String namespace = "namespace";
        IMessage noPartyIDs;

        IMessage message = new MapMessage(namespace, "ExecutionReport");
        IMessage header = new MapMessage(namespace, "header");
        IMessage trailer = new MapMessage(namespace, "trailer");

        header.addField("BeginString", "FIXT.1.1");
        header.addField("BodyLength", 20);
        header.addField("MsgType", "8");
        header.addField("MsgSeqNum", 1);
        header.addField("TargetCompID", "TCI");
        header.addField("SenderCompID", "SCI");
        trailer.addField("CheckSum", 10);
        message.addField("header", header);
        message.addField("trailer", trailer);
        message.addField("ClOrdID", "ClOrdID");

        List<IMessage> group = new ArrayList<>(actual);
        for(int i = 0; i < actual; i++) {
            noPartyIDs = new MapMessage(namespace, "NoPartyIDs");
            noPartyIDs.addField("PartyID", "PartyID");
            noPartyIDs.addField("PartyIDSource", 'B');
            noPartyIDs.addField("PartyRole", 1);
            group.add(noPartyIDs);
        }
        message.addField("group_NoPartyIDs", group);

        IMessage filter = new MapMessage(namespace, "ExecutionReport");
        header = new MapMessage(namespace, "header");
        trailer = new MapMessage(namespace, "trailer");

        header.addField("BeginString", "FIXT.1.1");
        header.addField("BodyLength", 21);
        header.addField("MsgType", "8");
        header.addField("MsgSeqNum", 2);
        header.addField("TargetCompID", "TCI*");
        trailer.addField("CheckSum", 11);
        filter.addField("header", header);
        filter.addField("trailer", trailer);

        switch(filterType) {
        case VALUE:
            group = new ArrayList<>(expected);
            for(int i = 0; i < expected; i++) {
                noPartyIDs = new MapMessage(namespace, "NoPartyIDs");
                noPartyIDs.addField("PartyID", "PartyID");
                noPartyIDs.addField("PartyRole", 1);
                group.add(noPartyIDs);
            }
            filter.addField("group_NoPartyIDs", group);
            break;
        case NOT_EMPTY:
            filter.addField("group_NoPartyIDs", StaticUtil.notNullFilter(0, null));
            break;
        case EMPTY:
            filter.addField("group_NoPartyIDs", StaticUtil.nullFilter(0, null));
            break;
        default:
            throw new IllegalArgumentException("No action for filter type " + filterType);
        }

        ComparatorSettings compSettings = new ComparatorSettings();
        MetaContainer metaContainer = new MetaContainer();
        metaContainer.setFailUnexpected(failUnexpected);
        MetaContainer NoContraBrokers = new MetaContainer();
        for (int i=0; i<expected; i++)
        {
            metaContainer.add("group_NoPartyIDs", NoContraBrokers);
        }

        compSettings.setMetaContainer(metaContainer);

        compSettings.setUncheckedFields(uncheckedFields);

        ComparisonResult table = MessageComparator.compare(message, filter, compSettings);
        return table;
    }

	@Test
	public void testCheckGroupOrder() {

        validateResult(doGroupCompare("Y", true, 2, new int [] {0, 1}, new int[] {0, 1}), 2, 0, 0);
        validateResult(doGroupCompare("Y", false, 2, new int [] {0, 1}, new int[] {0, 1}), 2, 0, 0);

        validateResult(doGroupCompare("Y", true, 2, new int [] {0, 1}, new int[] {1, 0}), 0, 2, 0);
        validateResult(doGroupCompare("Y", false, 2, new int [] {0, 1}, new int[] {1, 0}), 2, 0, 0);

        validateResult(doGroupCompare("Y", true, 3, new int [] {0, 1, 2}, new int[] {0, 1, 2}), 3, 0, 0);
        validateResult(doGroupCompare("Y", false, 3, new int [] {0, 1, 2}, new int[] {0, 1, 2}), 3, 0, 0);

        validateResult(doGroupCompare("Y", true, 3, new int [] {2, 1, 0}, new int[] {0, 1, 2}), 1, 2, 0);
        validateResult(doGroupCompare("Y", false, 3, new int [] {2, 1, 0}, new int[] {0, 1, 2}), 3, 0, 0);

        validateResult(doGroupCompare("Y", true, 4, new int [] {0, 1, 3}, new int[] {0, 1, 2}), 2, 1, 0);
        validateResult(doGroupCompare("Y", false, 4, new int [] {0, 1, 3}, new int[] {0, 1, 2}), 2, 1, 0);
    }

	private ComparisonResult doGroupCompare(String failUnexpected, boolean checkGroupOrder, int groupCount, int[] groupsMessage, int[] groupsFilter) {
	    IMessage[] subMessages = new IMessage[groupCount];

	    for (int i = 0; i < subMessages.length; i++) {
	        subMessages[i] = new MapMessage("namespace", "subMessage");
	        subMessages[i].addField("field", i);
        }

	    IMessage msg = new MapMessage("namespace", "name");
	    IMessage filter = new MapMessage("namespace", "name");

        List<IMessage> list = new ArrayList<>();
        for (int i = 0; i < groupsMessage.length; i++) {
            list.add(subMessages[groupsMessage[i]]);
        }
        msg.addField("group", list);

        list = new ArrayList<>();
        for (int i = 0; i < groupsFilter.length; i++) {
            list.add(subMessages[groupsFilter[i]]);
        }
        filter.addField("group", list);

        ComparatorSettings compSettings = new ComparatorSettings();
        compSettings.setCheckGroupsOrder(checkGroupOrder);
        MetaContainer metaContainer = new MetaContainer();
        metaContainer.setFailUnexpected(failUnexpected);
        compSettings.setMetaContainer(metaContainer);

        return MessageComparator.compare(msg, filter, compSettings);
    }

	@Test
	public void testDSGComparing()
	{

        HashMap<String, Boolean> map = new HashMap<>();
		map.put("OldGrossConsideration", new Boolean(true));

        IMessage mEXECUTION_REPORT = messageFactory.createMessage("EXECUTION_REPORT", "namespace");
        mEXECUTION_REPORT.addField("CounterParty", "CounterParty0");
		mEXECUTION_REPORT.addField("OrderStatus", "OrderStatus.Filled");
		mEXECUTION_REPORT.addField("Origin", 1);
		mEXECUTION_REPORT.addField("OldGrossConsideration", 0);
		mEXECUTION_REPORT.addField("SourceID", -1);
		mEXECUTION_REPORT.addField("IsSurveillance", (byte)0);
		mEXECUTION_REPORT.addField("TransactionID", -999L);
		mEXECUTION_REPORT.addField("InstrumentID", "689451");
		mEXECUTION_REPORT.addField("Side", "Side.Buy");
		mEXECUTION_REPORT.addField("VisibleSize", 0);
		mEXECUTION_REPORT.addField("LevelID", 0);
		mEXECUTION_REPORT.addField("ExecutionID", "ExecutionID");
		mEXECUTION_REPORT.addField("SecondaryClientOrderID", "A4");
		mEXECUTION_REPORT.addField("IsNamedOrder", (byte)0);
		mEXECUTION_REPORT.addField("OrderBookPriority", 0);
		mEXECUTION_REPORT.addField("OriginalVisibleSize", 10000);
		mEXECUTION_REPORT.addField("FirmID", "QA_AM_TEST");
		mEXECUTION_REPORT.addField("Value", 0);
		mEXECUTION_REPORT.addField("UsecWithinEntryTime", -999);
		mEXECUTION_REPORT.addField("ClientOrderLinkID", "D4");
		mEXECUTION_REPORT.addField("InitialEntryTime", -999);
		mEXECUTION_REPORT.addField("AutoInactivateOnDisconnect", "AutoInactivateOnDisconnect.No");
		mEXECUTION_REPORT.addField("TIF", "TIF.FOK");
		mEXECUTION_REPORT.addField("MinimumQuantity", 0);
		mEXECUTION_REPORT.addField("ContingentValue", 0);
		mEXECUTION_REPORT.addField("ContingentCondition", -1);
		mEXECUTION_REPORT.addField("TraderIndex", 5710);
        mEXECUTION_REPORT.addField("SourceGatewayType", new Byte("0"));
		mEXECUTION_REPORT.addField("ExecType", "ExecType.Fill");
		mEXECUTION_REPORT.addField("Symbol", "AKPS1");
		mEXECUTION_REPORT.addField("AllocatedSize", 0);
		mEXECUTION_REPORT.addField("PriorityIndicator", -1);
		mEXECUTION_REPORT.addField("OrderSubType", "OrderSubType.Order");
		mEXECUTION_REPORT.addField("OwnerID", "FIX_Fm_AM1");
		mEXECUTION_REPORT.addField("TotalSize", 0);
		mEXECUTION_REPORT.addField("OwnerIndex", 5710);
		mEXECUTION_REPORT.addField("GatewayRejection", "GatewayRejection.Open");
		mEXECUTION_REPORT.addField("BookDefinitionID", "NORMAL");
		mEXECUTION_REPORT.addField("VWAP", 150);
		mEXECUTION_REPORT.addField("InstrumentIndex", 1862);
		mEXECUTION_REPORT.addField("ExecutedPrice", 150);
		mEXECUTION_REPORT.addField("ConvertedValue", 0);
		mEXECUTION_REPORT.addField("OrderID", "OrderID");
		mEXECUTION_REPORT.addField("ExecutionInstruction", "ExecutionInstruction.None");
		mEXECUTION_REPORT.addField("OrderSeq", 0);
		mEXECUTION_REPORT.addField("TradeReportLinkID", "*");
		mEXECUTION_REPORT.addField("Request", 0);
		mEXECUTION_REPORT.addField("TraderID", "FIX_Fm_AM1");
		mEXECUTION_REPORT.addField("ClientID", "Anton4");
		mEXECUTION_REPORT.addField("ClientOrderID", "ClientOrderID");
		mEXECUTION_REPORT.addField("TradeReportID", "*");
		mEXECUTION_REPORT.addField("ExecutedValue", 150);
		mEXECUTION_REPORT.addField("OrderQty", 123);
		mEXECUTION_REPORT.addField("Action", "Action.None");
		mEXECUTION_REPORT.addField("OrderRejectCode", 0);
		mEXECUTION_REPORT.addField("OrderType", "OrderType.Market");
		mEXECUTION_REPORT.addField("AggressorSide", (byte)1);
		mEXECUTION_REPORT.addField("AccruedInterest", 0);
		mEXECUTION_REPORT.addField("HiddenSize", 0);
		mEXECUTION_REPORT.addField("CumulativeExecutedSize", 10000);
		mEXECUTION_REPORT.addField("Container", "Container.None");
		mEXECUTION_REPORT.addField("ExecutedSize", 10000);
		mEXECUTION_REPORT.addField("ExecutedYield", 0);
		mEXECUTION_REPORT.addField("OnlyForMarketDataSystem", "OnlyForMarketDataSystem.RegularTrade");
		mEXECUTION_REPORT.addField("Qualifier", "Qualifier.Conventional");
		mEXECUTION_REPORT.addField("SideQualifier", "SideQualifier.BUY");
        mEXECUTION_REPORT.addField("CrossType", new Byte("0"));
		mEXECUTION_REPORT.addField("TransactTime", "*");
		mEXECUTION_REPORT.addField("RoutingSeq", -999L);
		mEXECUTION_REPORT.addField("ClearingAccountType", "ClearingAccountType.House");
		mEXECUTION_REPORT.addField("EntryTimeInSeconds", -999);
		mEXECUTION_REPORT.addField("Capacity", "Capacity.Principal");

        ComparatorSettings compSettings = new ComparatorSettings();
		compSettings.setNegativeMap(map);

        ComparisonResult table = MessageComparator.compare(mEXECUTION_REPORT, mEXECUTION_REPORT, compSettings);
		System.out.println(table);
	}

	@Test
	public void testComparingWithNegativeMap()
	{

        HashMap<String, Boolean> map = new HashMap<>();
		map.put("OldGrossConsideration", true);
		map.put("LevelID", true);
		map.put("OrderStatus", true);

        IMessage mEXECUTION_REPORT = messageFactory.createMessage("EXECUTION_REPORT", "namespace");
        mEXECUTION_REPORT.addField("CounterParty", "CounterParty");
		mEXECUTION_REPORT.addField("OrderStatus", "OrderStatus.Filled");
		mEXECUTION_REPORT.addField("Origin", 1);
		mEXECUTION_REPORT.addField("OldGrossConsideration", 0);
		mEXECUTION_REPORT.addField("SourceID", -1);
		mEXECUTION_REPORT.addField("IsSurveillance", (byte)0);
		mEXECUTION_REPORT.addField("TransactionID", -999L);
		mEXECUTION_REPORT.addField("InstrumentID", "689451");
		mEXECUTION_REPORT.addField("Side", "Side.Buy");
		mEXECUTION_REPORT.addField("VisibleSize", 0);
		mEXECUTION_REPORT.addField("LevelID", 0);

        ComparatorSettings compSettings = new ComparatorSettings();
		compSettings.setNegativeMap(map);

		IMessage msg = mEXECUTION_REPORT.cloneMessage();
		msg.addField("LevelID", 1);

        ComparisonResult table = MessageComparator.compare(mEXECUTION_REPORT, msg, compSettings);
		System.out.println(table);
	}

	@Test
	public void testComparingPresentMissedEnums() {

        HashMap<String, Boolean> map = new HashMap<>();
		map.put("OldGrossConsideration", true);
		map.put("LevelID", true);
		map.put("OrderStatus", true);

        IMessage mEXECUTION_REPORT = messageFactory.createMessage("EXECUTION_REPORT", "namespace");
        mEXECUTION_REPORT.addField("CounterParty", "CounterParty");
		mEXECUTION_REPORT.addField("OrderStatus", "OrderStatus.Filled");
		mEXECUTION_REPORT.addField("Origin", 1);
		mEXECUTION_REPORT.addField("OldGrossConsideration", 0);
		mEXECUTION_REPORT.addField("SourceID", -1);
		mEXECUTION_REPORT.addField("IsSurveillance", (byte)0);
		mEXECUTION_REPORT.addField("TransactionID", -999L);
		mEXECUTION_REPORT.addField("InstrumentID", "689451");
		mEXECUTION_REPORT.addField("Side", "Side.Buy");
		mEXECUTION_REPORT.addField("VisibleSize", 0);
		mEXECUTION_REPORT.addField("LevelID", 0);

        ComparatorSettings compSettings = new ComparatorSettings();
		compSettings.setNegativeMap(map);

        IMessage mEXECUTION_REPORT_FILTER = messageFactory.createMessage("EXECUTION_REPORT", "namespace");
        mEXECUTION_REPORT_FILTER.addField("CounterParty", "CounterParty");
		mEXECUTION_REPORT_FILTER.addField("OrderStatus", "OrderStatus.Present");
		mEXECUTION_REPORT_FILTER.addField("Origin", 1);
		mEXECUTION_REPORT_FILTER.addField("OldGrossConsideration", 0);
		mEXECUTION_REPORT_FILTER.addField("SourceID", -1);
		mEXECUTION_REPORT_FILTER.addField("IsSurveillance", (byte)0);
		mEXECUTION_REPORT_FILTER.addField("TransactionID", -999L);
		mEXECUTION_REPORT_FILTER.addField("InstrumentID", "689451");
		mEXECUTION_REPORT_FILTER.addField("Side", "Side.Present");
		mEXECUTION_REPORT_FILTER.addField("VisibleSize", 0);
		mEXECUTION_REPORT_FILTER.addField("LevelID", 0);
		mEXECUTION_REPORT_FILTER.addField("AutoInactivateOnDisconnect", "AutoInactivateOnDisconnect.Missed");

        ComparisonResult table = MessageComparator.compare(mEXECUTION_REPORT, mEXECUTION_REPORT_FILTER, compSettings);
		System.out.println(table);
	}

	@Test
	public void testCompareMultipleSubmessages()
	{
		Map<String, Object> actions = new HashMap<>();

        IMessage messageOTHRPRTY = messageFactory.createMessage("OTHRPRTY", "namespace");
        messageOTHRPRTY.addField("CounterParty", new String("CounterParty"));
		actions.put("othrprty", messageOTHRPRTY);

        IMessage messageSETPRTY = messageFactory.createMessage("SETPRTY", "namespace");
        messageSETPRTY.addField("SettlementAccount", new String("SettlementAccount_1"));
        messageSETPRTY.addField("SettlementParty_Seller_R", new String("SettlementParty_Seller_R_1"));
        messageSETPRTY.addField("PlaceOfSettlement", new String("PlaceOfSettlement_1"));
        messageSETPRTY.addField("SettlementParty_Seller_P", new String("SettlementParty_Seller_P_1"));
        messageSETPRTY.addField("SettlementParty_Buyer_R", new String("SettlementParty_Buyer_R_1"));
        messageSETPRTY.addField("SettlementParty_Buyer_P", new String("SettlementParty_Buyer_P_1"));
		actions.put("setprty1", messageSETPRTY);

        messageSETPRTY = messageFactory.createMessage("SETPRTY", "namespace");
        messageSETPRTY.addField("SettlementAccount", new String("SettlementAccount_2"));
        messageSETPRTY.addField("SettlementParty_Seller_R", new String("SettlementParty_Seller_R_2"));
        messageSETPRTY.addField("PlaceOfSettlement", new String("PlaceOfSettlement_2"));
        messageSETPRTY.addField("SettlementParty_Seller_P", new String("SettlementParty_Seller_P_2"));
        messageSETPRTY.addField("SettlementParty_Buyer_R", new String("SettlementParty_Buyer_R_2"));
        messageSETPRTY.addField("SettlementParty_Buyer_P", new String("SettlementParty_Buyer_P_2"));
		actions.put("setprty2", messageSETPRTY);

        IMessage messageSETDET = messageFactory.createMessage("SETDET", "namespace");
        messageSETDET.addField("TypeOfSettlementIndicator", new String("TypeOfSettlementIndicator"));
		messageSETDET.addField("SETPRTY", actions.get("setprty1"));
		messageSETDET.addField("SETPRTY", actions.get("setprty2"));
		actions.put("setdet", messageSETDET);

        IMessage messageBasicHeader = messageFactory.createMessage("BasicHeader", "namespace");
		messageBasicHeader.addField("ServiceID", 1 );
        messageBasicHeader.addField("LogicalTerminalAddress", new String("ANASCH20AXXX"));
		messageBasicHeader.addField("SessionNumber", 4321 );
		messageBasicHeader.addField("SequenceNumber", 654321 );
        messageBasicHeader.addField("ApplicationID", new String("F"));
		actions.put("bh", messageBasicHeader);

        IMessage messageCONFPRTY = messageFactory.createMessage("CONFPRTY", "namespace");
        messageCONFPRTY.addField("ClearingMember_P", new String("ClearingMember_P"));
        messageCONFPRTY.addField("PartyCapacity", new String("PartyCapacity"));
        messageCONFPRTY.addField("AccountAtTradingVenue", new String("AccountAtTradingVenue"));
        messageCONFPRTY.addField("ClearingMember_R", new String("ClearingMember_R"));
        messageCONFPRTY.addField("Buyer_P", new String("Buyer_P"));
        messageCONFPRTY.addField("PositionAccount", new String("PositionAccount"));
        messageCONFPRTY.addField("Buyer_R", new String("Buyer_R"));
        messageCONFPRTY.addField("TradingPartyReference", new String("TradingPartyReference"));
        messageCONFPRTY.addField("PartyNarrative", new String("PartyNarrative"));
        messageCONFPRTY.addField("Seller_P", new String("Seller_P"));
        messageCONFPRTY.addField("Seller_R", new String("Seller_R"));
		actions.put("confprty", messageCONFPRTY);

        IMessage messageCONFDET = messageFactory.createMessage("CONFDET", "namespace");
        messageCONFDET.addField("PlaceOfTrade", new String("PlaceOfTrade"));
        messageCONFDET.addField("SettlementDate", new String("SettlementDate"));
		messageCONFDET.addField("CONFPRTY", actions.get("confprty"));
        messageCONFDET.addField("QuantityTraded", new String("QuantityTraded"));
        messageCONFDET.addField("DealPriceAndCurrency_B", new String("DealPriceAndCurrency_B"));
        messageCONFDET.addField("BuySellIndicator", new String("BuySellIndicator"));
        messageCONFDET.addField("SettlementAmountAndCurrency", new String("SettlementAmountAndCurrency"));
        messageCONFDET.addField("DealPriceAndCurrency_A", new String("DealPriceAndCurrency_A"));
        messageCONFDET.addField("TradeDateTime", new String("TradeDateTime"));
        messageCONFDET.addField("FinancialInstrument", new String("FinancialInstrument"));
        messageCONFDET.addField("PaymentIndicator", new String("PaymentIndicator"));
        messageCONFDET.addField("TradeProcessingNarrative", new String("TradeProcessingNarrative"));
		actions.put("confdet", messageCONFDET);

        IMessage messageLINK = messageFactory.createMessage("LINK", "namespace");
        messageLINK.addField("LinkedMessage", new String("LinkedMessage"));
        messageLINK.addField("TradeReference", new String("TradeReference"));
        messageLINK.addField("PreviousReference", new String("PreviousReference"));
        messageLINK.addField("VenueTradeReference", new String("VenueTradeReference"));
		actions.put("link", messageLINK);

        IMessage messageGENL = messageFactory.createMessage("GENL", "namespace");
        messageGENL.addField("TradeType", new String("TradeType"));
        messageGENL.addField("SendersReference", new String("SendersReference"));
        messageGENL.addField("PreparationDateTime", new String("PreparationDateTime"));
		messageGENL.addField("LINK", actions.get("link"));
		actions.put("genl", messageGENL);

        IMessage messageTrailer = messageFactory.createMessage("Trailer", "namespace");
        messageTrailer.addField("TNG", new String("TNG"));
        messageTrailer.addField("CHK", new String("CHK"));
        messageTrailer.addField("MAC", new String("MAC"));
		actions.put("trailer", messageTrailer);

        IMessage messageApplicationHeaderInput = messageFactory.createMessage("ApplicationHeaderInput", "namespace");
		messageApplicationHeaderInput.addField("ObsolescencePeriod", 3 );
        messageApplicationHeaderInput.addField("Input", new String("I"));
        messageApplicationHeaderInput.addField("ReceiversAddress", new String("BANKDEFFXXXX"));
        messageApplicationHeaderInput.addField("MessagePriority", new String("U"));
		messageApplicationHeaderInput.addField("MessageType", 518 );
        messageApplicationHeaderInput.addField("DeliveryMonitoring", new String("3"));
		actions.put("h1", messageApplicationHeaderInput);

        IMessage messageTradeConfirmation = messageFactory.createMessage("TradeConfirmation", "namespace");
		messageTradeConfirmation.addField("OTHRPRTY", actions.get("othrprty"));
		messageTradeConfirmation.addField("SETDET", actions.get("setdet"));
		messageTradeConfirmation.addField("BasicHeader", actions.get("bh"));
		messageTradeConfirmation.addField("CONFDET", actions.get("confdet"));
		messageTradeConfirmation.addField("GENL", actions.get("genl"));
		messageTradeConfirmation.addField("Trailer", actions.get("trailer"));
		messageTradeConfirmation.addField("ApplicationHeaderInput", actions.get("h1"));

        messageSETPRTY = messageFactory.createMessage("SETPRTY", "namespace");
        messageSETPRTY.addField("SettlementAccount", new String("another value"));
        messageSETPRTY.addField("SettlementParty_Seller_R", new String("another value"));
        messageSETPRTY.addField("PlaceOfSettlement", new String("another value"));
		actions.put("setprty2", messageSETPRTY);

        messageSETDET = messageFactory.createMessage("SETDET", "namespace");
        messageSETDET.addField("TypeOfSettlementIndicator", new String("TypeOfSettlementIndicator"));
		messageSETDET.addField("SETPRTY", messageSETPRTY);
		messageSETDET.addField("SETPRTY", actions.get("setprty1"));
		actions.put("setdet", messageSETDET);

        IMessage messageTradeConfirmation2 = messageFactory.createMessage("TradeConfirmation", "namespace");
		messageTradeConfirmation2.addField("OTHRPRTY", actions.get("othrprty"));
		messageTradeConfirmation2.addField("SETDET", actions.get("setdet"));
		messageTradeConfirmation2.addField("BasicHeader", actions.get("bh"));
		messageTradeConfirmation2.addField("CONFDET", actions.get("confdet"));
		messageTradeConfirmation2.addField("GENL", actions.get("genl"));
		messageTradeConfirmation2.addField("Trailer", actions.get("trailer"));
		messageTradeConfirmation2.addField("ApplicationHeaderInput", actions.get("h1"));

        ComparatorSettings compSettings = new ComparatorSettings();

        ComparisonResult table = MessageComparator.compare(messageTradeConfirmation, messageTradeConfirmation2, compSettings);
		System.out.println(table);
        //System.out.println(ComparisonUtil.toTable(table).toString());

	}

    @Test
	public void testDoublePresicion()
	{

        IMessage mGrossRecord = messageFactory.createMessage("GrossRecord", "namespace");
		mGrossRecord.addField("CurrentGross", 89518.08d);

        IMessage mGrossRecord2 = messageFactory.createMessage("GrossRecord", "namespace");
		mGrossRecord2.addField("CurrentGross", 89519d);

        MetaContainer metaContainer = new MetaContainer();
		metaContainer.addDoublePrecision("CurrentGross=10");

        ComparatorSettings settings = new ComparatorSettings();
		settings.setMetaContainer(metaContainer);

        ComparisonResult result = MessageComparator.compare(mGrossRecord, mGrossRecord2, settings);
        Assert.assertEquals(StatusType.PASSED, result.getResult("CurrentGross").getStatus());
		System.out.println(result);
	}


	@Test
	public void testDoublePre()
	{
        IMessage mGrossRecord = messageFactory.createMessage("GrossRecord", "namespace");
		mGrossRecord.addField("BrokerId", "MEMBERFIRM1");
		mGrossRecord.addField("CurrentGross", 74598.4);
		mGrossRecord.addField("UserId", "NAT_MAX_1");

        IMessage mGrossRecord2 = messageFactory.createMessage("GrossRecord", "namespace");
		mGrossRecord2.addField("BrokerId", "MEMBERFIRM1");
		mGrossRecord2.addField("CurrentGross", 74599);
		mGrossRecord2.addField("UserId", "NAT_MAX_1");

        MetaContainer metaContainer = new MetaContainer();
		metaContainer.addDoublePrecision("CurrentGross=10");

        ActionContext settings = new ActionContext(getScriptContext(), true);
		settings.setMetaContainer(metaContainer);
		settings.setFailUnexpected("N");
		settings.setDescription("check CGC in database");
		settings.setAddToReport(true);

        ComparatorSettings compSettings = new ComparatorSettings();
		compSettings.setMetaContainer(metaContainer);

		//mGrossRecord = SPOActions.SPO_CheckDbCGC(settings, mGrossRecord);
        ComparisonResult result = MessageComparator.compare(mGrossRecord, mGrossRecord2, compSettings);
		System.out.println(result);

	}

	@Test
    public void testAnyRepeatingGroup() throws IOException, ProtocolCodecException
    {
        IMessage message = new MapMessage("Message", "namespace");
        IMessage filter = new MapMessage("Message", "namespace");

        List<IMessage> list = new ArrayList<>();
        IMessage group = new MapMessage("Group", "namespace");
        group.addField("269", '1');
        group.addField("270", 20.01D);
        group.addField("271", 40.01D);

        list.add(group);
        list.add(group);

        message.addField("Group", list);
        filter.addField("Group", StaticUtil.notNullFilter(0, null));

        ComparatorSettings compSettings = new ComparatorSettings();
        MetaContainer metaContainer = new MetaContainer();

        compSettings.setMetaContainer(metaContainer);

        ComparisonResult comparisonResult = MessageComparator.compare(message, filter, compSettings);
        System.out.println(comparisonResult);
        Assert.assertEquals(0, ComparisonUtil.getResultCount(comparisonResult, StatusType.FAILED));
        Assert.assertEquals(8, ComparisonUtil.getResultCount(comparisonResult, StatusType.NA));
        Assert.assertEquals(1, ComparisonUtil.getResultCount(comparisonResult, StatusType.PASSED));

        metaContainer.setFailUnexpected("Y");
        comparisonResult = MessageComparator.compare(message, filter, compSettings);
        System.out.println(comparisonResult);
        Assert.assertEquals(0, ComparisonUtil.getResultCount(comparisonResult, StatusType.FAILED));
        Assert.assertEquals(8, ComparisonUtil.getResultCount(comparisonResult, StatusType.NA));
        Assert.assertEquals(1, ComparisonUtil.getResultCount(comparisonResult, StatusType.PASSED));

        metaContainer.setFailUnexpected("A");
        comparisonResult = MessageComparator.compare(message, filter, compSettings);
        System.out.println(comparisonResult);
        Assert.assertEquals(0, ComparisonUtil.getResultCount(comparisonResult, StatusType.FAILED));
        Assert.assertEquals(8, ComparisonUtil.getResultCount(comparisonResult, StatusType.NA));
        Assert.assertEquals(1, ComparisonUtil.getResultCount(comparisonResult, StatusType.PASSED));
    }

	@Test
    public void testUnchecked() throws IOException, ProtocolCodecException
    {
        IMessage message = new MapMessage("MarketDataSnapshotFullRefresh", "namespace");
        IMessage filter = new MapMessage("MarketDataSnapshotFullRefresh", "namespace");


        IMessage subMsg1 = new MapMessage("List", "namespace");
        subMsg1.addField("LP", '1');
        subMsg1.addField("LN", '2');
        subMsg1.addField("LF", '3');

        IMessage subMsg2 = new MapMessage("List", "namespace");
        subMsg2.addField("LP", '1');
        subMsg2.addField("LF", '4');

        List<IMessage> list1 = new ArrayList<>();
        List<IMessage> list2 = new ArrayList<>();

        list1.add(subMsg1);
        list1.add(subMsg1);

        list2.add(subMsg2);
        list2.add(subMsg2);

        subMsg1 = new MapMessage("Message", "namespace");
        subMsg1.addField("MP", '1');
        subMsg1.addField("MN", '2');
        subMsg1.addField("MF", '3');

        subMsg2 = new MapMessage("Message", "namespace");
        subMsg2.addField("MP", '1');
        subMsg2.addField("MF", '4');

        message.addField("FieldL", list1);
        message.addField("FieldM", subMsg1);
        message.addField("P", '1');
        message.addField("N", '2');
        message.addField("F", '3');
        message.addField("Sub", message.cloneMessage());

        filter.addField("FieldL", list2);
        filter.addField("FieldM", subMsg2);
        filter.addField("P", '1');
        filter.addField("F", '4');
        filter.addField("Sub", filter.cloneMessage());

        MetaContainer metaContainer = new MetaContainer();
        metaContainer.setFailUnexpected("A");

        ComparatorSettings compSettings = new ComparatorSettings();
        Set<String> set = new HashSet<>();
        set.add("N");
        set.add("FieldL");
        set.add("FieldM");
        set.add("List"); // No effect
        set.add("Message"); // No effect
        set.add("MarketDataSnapshotFullRefresh"); // No effect
        compSettings.setUncheckedFields(set);
        compSettings.setMetaContainer(metaContainer);

        ComparisonResult comparisonResult = MessageComparator.compare(message, filter, compSettings);
        System.out.println(comparisonResult);
        Assert.assertEquals(8, ComparisonUtil.getResultCount(comparisonResult, StatusType.FAILED));
        Assert.assertEquals(8, ComparisonUtil.getResultCount(comparisonResult, StatusType.NA));
        Assert.assertEquals(8, ComparisonUtil.getResultCount(comparisonResult, StatusType.PASSED));
    }

	@Test
    public void compareMessages() throws IOException, ProtocolCodecException
    {
        IMessage msg1 = new MapMessage("MarketDataSnapshotFullRefresh", "namespace");
        IMessage msg2 = new MapMessage("MarketDataSnapshotFullRefresh", "namespace");

        List<IMessage> list = new ArrayList<>();
        IMessage group = new MapMessage("MDEntries", "namespace");
        group.addField("269", '1');
        group.addField("270", 20.01D);
        group.addField("271", 40.01D);

        list.add(group);
        group = new MapMessage("MDEntries", "namespace");

        group.addField("269", '2');
        group.addField("270", 20.01D);
        group.addField("271", 40.01D);

        list.add(group);
        msg1.addField("MDEntries", list);

        list = new ArrayList<>();
        list.add(group);
        msg2.addField("MDEntries", list);

        ComparatorSettings compSettings = new ComparatorSettings();

        long startTime = System.currentTimeMillis();
        ComparisonResult comparisonResult = MessageComparator.compare(msg1, msg2, compSettings);
        System.out.printf("diff %d\n", System.currentTimeMillis() - startTime);
        System.out.println(comparisonResult);
        Assert.assertEquals(0, ComparisonUtil.getResultCount(comparisonResult, StatusType.FAILED));
    }

	@Test
	public void compareLargeMessages() throws IOException, ProtocolCodecException
    {
	    int count = 500;

	    IMessage msg1 = new MapMessage("MarketDataSnapshotFullRefresh", "namespace");

	    IMessage msgHeader = new MapMessage("Header", "namespace");
	    msgHeader.addField("8", "FIXT.1.1");
	    msgHeader.addField("9", "11789");
	    msgHeader.addField("35", "W");
        msgHeader.addField("49", "Sender");
	    msgHeader.addField("56", "PVMEMD");
	    msgHeader.addField("34", "251");
	    msgHeader.addField("52", "20150429-15:30:19.900");

	    msg1.addField("header", msgHeader);
	    msg1.addField("262", "1430321440762");
	    msg1.addField("55", "[N/A]");
	    msg1.addField("48", "EURv3MEURIB:(10Yv20Y)");
	    msg1.addField("22", "101");
	    msg1.addField("268", count);

	    IMessage msg2 = msg1.cloneMessage();

	    IMessage group = new MapMessage("MDEntries", "namespace");
        group.addField("269", '2');
        group.addField("270", 40.01D);
        group.addField("271", 20.01D);
        group.addField("273", DateTimeUtility.nowLocalDateTime());
        group.addField("274", DateTimeUtility.nowLocalDate());
        group.addField("275", DateTimeUtility.nowLocalTime());
        group.addField("336", 7);
        group.addField("326", 17);

        List<IMessage> list = new ArrayList<>();
	    for (int i = 0; i < count; i++) {
	        list.add(group);
        }
	    msg1.addField("MDEntries", list);

	    List<IMessage> list2 = new ArrayList<>();
	    list2.add(group);
	    msg2.addField("MDEntries", list2);

        ComparatorSettings compSettings = new ComparatorSettings();

        long startTime = System.currentTimeMillis();
        ComparisonResult comparisonResult = MessageComparator.compare(msg1, msg2, compSettings);
        System.out.printf("diff %d\n", System.currentTimeMillis() - startTime);
        Assert.assertEquals(0, ComparisonUtil.getResultCount(comparisonResult, StatusType.FAILED));
    }

    @Test
    public void testFilters() throws FileNotFoundException, IOException {
        IDictionaryStructure dictionary = null;

        try(InputStream stream = new FileInputStream("src/test/workspace/cfg/dictionaries/test_aml.xml")) {
            IDictionaryStructureLoader loader = new XmlDictionaryStructureLoader();
            dictionary = loader.load(stream);
        }

        Assert.assertNotNull("dictionary cannot be null", dictionary);

        ComparatorSettings settings = new ComparatorSettings().setDictionaryStructure(dictionary);
        IMessage message = new MapMessage(dictionary.getNamespace(), "ArrayMessage");
        IMessage filter = message.cloneMessage();

        filter.addField("MessageArray", StaticUtil.nullFilter(0, null));
        ComparisonResult result = MessageComparator.compare(message, filter, settings);

        Assert.assertEquals(1, ComparisonUtil.getResultCount(result, StatusType.PASSED));
        Assert.assertEquals(0, ComparisonUtil.getResultCount(result, StatusType.FAILED));
    }

    @Test
    public void testIgnoredFields() throws Exception {
        ComparatorSettings settings = new ComparatorSettings().setIgnoredFields(singleton("filler"));

        IMessage message = new MapMessage("TEST", "TestMessage");
        IMessage filter = message.cloneMessage();

        message.addField("valid", "a");
        message.addField("filler", " ");

        filter.addField("valid", "a");
        filter.addField("filler", "failed");

        ComparisonResult result = MessageComparator.compare(message, filter, settings);

        Assert.assertThat(result.getResult("valid").getStatus(), CoreMatchers.is(StatusType.PASSED));
        Assert.assertThat(result.getResult("filler").getStatus(), CoreMatchers.is(StatusType.NA));

        message = new MapMessage("TEST", "TestMessage");
        filter = message.cloneMessage();

        IMessage subMessage = new MapMessage("TEST", "filler");
        subMessage.addField("a", "a");
        message.addField("filler", subMessage);

        IMessage filterSubMessage = new MapMessage("TEST", "filler");
        filterSubMessage.addField("a", "b");
        filter.addField("filler", filterSubMessage);

        result = MessageComparator.compare(message, filter, settings);
        Assert.assertThat(result.getResult("filler").getStatus(), CoreMatchers.is(StatusType.NA));
        result = result.getResult("filler");
        Assert.assertThat(result.getResult("a").getStatus(), CoreMatchers.is(StatusType.NA));
    }

    @Test
    public void testKeyField() {
        // non matching key field
        IMessage message = createMessage(msg -> {
            msg.addField("keyField", 1);
            msg.addField("nonKeyField", 2);
        });

        IMessage filter = createMessage(msg -> {
            msg.addField("keyField", 2);
            msg.addField("nonKeyField", 3);
        });

        ComparatorSettings settings = new ComparatorSettings();

        settings.getMetaContainer().setKeyFields(singleton("keyField"));

        ComparisonResult result = MessageComparator.compare(message, filter, settings);

        Assert.assertNull(result);

        // matching key field, but non matching regular field
        filter.addField("keyField", 1);

        result = MessageComparator.compare(message, filter, settings);

        Assert.assertNotNull(result);
        Assert.assertEquals(1, ComparisonUtil.getResultCount(result, StatusType.PASSED));
        Assert.assertEquals(1, ComparisonUtil.getResultCount(result, StatusType.FAILED));
        Assert.assertTrue("keyField is not key in " + result, result
                .getResult("keyField")
                .isKey());
    }

    @Test
    public void testKeyFieldInSubmessage() {
        // non matching key field
        IMessage message = createMessage(msg -> {
            msg.addField("rootField", 1);
            msg.addField("subMessage", createMessage(subMsg -> {
                subMsg.addField("keyField", 1);
                subMsg.addField("nonKeyField1", 2);
                subMsg.addField("nonKeyField2", 3);
            }));
        });

        IMessage filter = createMessage(msg -> {
            msg.addField("rootField", 1);
            msg.addField("subMessage", createMessage(subMsg -> {
                subMsg.addField("keyField", 2);
                subMsg.addField("nonKeyField1", 2);
                subMsg.addField("nonKeyField2", 3);
            }));
        });

        ComparatorSettings settings = new ComparatorSettings();
        MetaContainer metaContainer = settings.getMetaContainer();

        metaContainer.add("subMessage", new MetaContainer().setKeyFields(singleton("keyField")));

        ComparisonResult result = MessageComparator.compare(message, filter, settings);

        Assert.assertNull(result);

        // matching key field, but non matching regular field
        IMessage subMessage = filter.getField("subMessage");
        subMessage.addField("keyField", 1);
        subMessage.addField("nonKeyField1", 1);

        result = MessageComparator.compare(message, filter, settings);

        Assert.assertNotNull(result);
        Assert.assertEquals(3, ComparisonUtil.getResultCount(result, StatusType.PASSED));
        Assert.assertEquals(1, ComparisonUtil.getResultCount(result, StatusType.FAILED));
        Assert.assertTrue("keyField is not key in " + result, result
                .getResult("subMessage")
                .getResult("keyField")
                .isKey());

        // override key field in submessage by marking submessage field as a key one
        metaContainer.setKeyFields(singleton("subMessage"));

        result = MessageComparator.compare(message, filter, settings);

        Assert.assertNull(result);
    }

    @Test
    public void testKeyFieldInSubmessageCollection() {
        // non matching key field
        IMessage message = createMessage(msg -> {
            msg.addField("rootField", 1);
            msg.addField("collection", asList(
                    createMessage(subMsg -> {
                        subMsg.addField("keyField", 3);
                        subMsg.addField("nonKeyField", 4);
                    }),
                    createMessage(subMsg -> {
                        subMsg.addField("keyField", 1);
                        subMsg.addField("nonKeyField", 2);
                    })
            ));
        });

        IMessage filter = createMessage(msg -> {
            msg.addField("rootField", 1);
            msg.addField("collection", asList(
                    createMessage(subMsg -> {
                        subMsg.addField("keyField", 1);
                        subMsg.addField("nonKeyField", 2);
                    }),
                    createMessage(subMsg -> {
                        subMsg.addField("keyField", 2);
                        subMsg.addField("nonKeyField", 4);
                    })
            ));
        });

        ComparatorSettings settings = new ComparatorSettings();
        MetaContainer metaContainer = settings.getMetaContainer();

        metaContainer.add("collection", new MetaContainer()); // does not have key fields
        metaContainer.add("collection", new MetaContainer().setKeyFields(singleton("keyField"))); // have single key field

        ComparisonResult result = MessageComparator.compare(message, filter, settings);

        Assert.assertNull(result);

        // matching key field, but non matching regular field
        IMessage collectionElement = filter.<List<IMessage>>getField("collection").get(1);
        collectionElement.addField("keyField", 3);
        collectionElement.addField("nonKeyField", 5);

        result = MessageComparator.compare(message, filter, settings);

        Assert.assertNotNull(result);
        Assert.assertEquals(4, ComparisonUtil.getResultCount(result, StatusType.PASSED));
        Assert.assertEquals(1, ComparisonUtil.getResultCount(result, StatusType.FAILED));
        Assert.assertFalse("keyField is a key in 0 result " + result,
                result.getResult("collection")
                        .getResult("0")
                        .getResult("keyField")
                        .isKey());
        Assert.assertTrue("keyField is not a key in 1 result " + result,
                result.getResult("collection")
                        .getResult("1")
                        .getResult("keyField")
                        .isKey());

        // override key field in collection by marking collection field as a key one
        metaContainer.setKeyFields(singleton("collection"));

        result = MessageComparator.compare(message, filter, settings);

        Assert.assertNull(result);
    }

    @Test
    public void testKeyFieldInSubMessageCollectionKeyFieldIsNotFirst() {
        IMessage message = createMessage(msg -> {
            msg.addField("rootField", 1);
            msg.addField("collection", asList(
                    createMessage(subMsg -> {
                        subMsg.addField("nonKeyField", 2);
                        subMsg.addField("anotherInnerMessage", createMessage(innerMsg -> {
                            innerMsg.addField("anotherField", 1);
                        }));
                    }),
                    createMessage(subMsg -> {
                        subMsg.addField("nonKeyField", 4);
                        subMsg.addField("innerMessage", createMessage(innerMsg -> {
                            innerMsg.addField("keyField", 1);
                        }));
                    })
            ));
        });

        IMessage wrongFilter = createMessage(msg -> {
            msg.addField("collection", asList(
                    createMessage(subMsg -> {
                        subMsg.addField("nonKeyField", 2);
                        subMsg.addField("anotherInnerMessage", createMessage(innerMsg -> {
                            innerMsg.addField("anotherField", 2); // does not match
                        }));
                    }),
                    createMessage(subMsg -> {
                        subMsg.addField("innerMessage", createMessage(innerMsg -> {
                            innerMsg.addField("keyField", 1);
                        }));
                    })
            ));
        });

        IMessage rightFilter = wrongFilter.cloneMessage();
        rightFilter.<List<IMessage>>getField("collection").get(0)
                .<IMessage>getField("anotherInnerMessage").addField("anotherField", 1); // match the message

        ComparatorSettings settings = new ComparatorSettings();
        MetaContainer metaContainer = settings.getMetaContainer();

        metaContainer.add("collection", new MetaContainer());

        MetaContainer innerContainer = new MetaContainer();
        innerContainer.add("innerMessage", new MetaContainer().setKeyFields(singleton("keyField")));
        metaContainer.add("collection", innerContainer);

        ComparisonResult result = MessageComparator.compare(message, wrongFilter, settings);
        Assert.assertNotNull("Key is not matched", result);
        Assert.assertEquals(2, ComparisonUtil.getResultCount(result, StatusType.PASSED));
        Assert.assertEquals(1, ComparisonUtil.getResultCount(result, StatusType.FAILED));
        Assert.assertTrue("keyField is not a key in 1 result " + result,
                result.getResult("collection")
                        .getResult("1")
                        .getResult("innerMessage")
                        .getResult("keyField")
                        .isKey());

        result = MessageComparator.compare(message, rightFilter, settings);
        Assert.assertNotNull("Key is not matched", result);
        Assert.assertEquals(3, ComparisonUtil.getResultCount(result, StatusType.PASSED));
        Assert.assertEquals(0, ComparisonUtil.getResultCount(result, StatusType.FAILED));
        Assert.assertTrue("keyField is not a key in 1 result " + result,
                result.getResult("collection")
                        .getResult("1")
                        .getResult("innerMessage")
                        .getResult("keyField")
                        .isKey());
    }

    @Test
    public void testMessageAsKeyField() {
        IMessage message = createMessage(root -> {
            root.addField("inner", createMessage(inner -> {
                inner.addField("test", 1);
            }));
        });

        IMessage correctFilter = createMessage(root -> {
            root.addField("inner", createMessage(inner -> {
                inner.addField("test", 1);
            }));
        });

        IMessage wrongFilter = createMessage(root -> {
            root.addField("inner", createMessage(inner -> {
                inner.addField("test", 2);
            }));
        });

        ComparatorSettings settings = new ComparatorSettings();
        MetaContainer metaContainer = settings.getMetaContainer();
        metaContainer.setKeyFields(singleton("inner"));

        ComparisonResult result = MessageComparator.compare(message, wrongFilter, settings);
        Assert.assertNull(result);

        result = MessageComparator.compare(message, correctFilter, settings);
        Assert.assertNotNull(result);
        Assert.assertEquals(1, ComparisonUtil.getResultCount(result, StatusType.PASSED));
        Assert.assertEquals(0, ComparisonUtil.getResultCount(result, StatusType.FAILED));
        Assert.assertTrue("inner is not a key in 1 result " + result,
                result.getResult("inner")
                        .isKey());
    }

    @Test
    public void testMessageInCollectionAsKeyField() {
        IMessage message = createMessage(root -> {
            root.addField("collection", asList(
                    createMessage(inner -> {
                        inner.addField("test", 1);
                    }),
                    createMessage(inner -> {
                        inner.addField("key", createMessage(key -> {
                            key.addField("test", 1);
                        }));
                    })
            ));
        });

        IMessage correctFilter = createMessage(root -> {
            root.addField("collection", asList(
                    createMessage(inner -> {
                        inner.addField("test", 1);
                    }),
                    createMessage(inner -> {
                        inner.addField("key", createMessage(key -> {
                            key.addField("test", 1);
                        }));
                    })
            ));
        });

        IMessage wrongFilter = createMessage(root -> {
            root.addField("collection", asList(
                    createMessage(inner -> {
                        inner.addField("test", 1);
                    }),
                    createMessage(inner -> {
                        inner.addField("key", createMessage(key -> {
                            key.addField("test", 2);
                        }));
                    })
            ));
        });

        ComparatorSettings settings = new ComparatorSettings();
        MetaContainer metaContainer = settings.getMetaContainer();

        metaContainer.add("collection", new MetaContainer());
        metaContainer.add("collection", new MetaContainer().setKeyFields(singleton("key")));

        ComparisonResult result = MessageComparator.compare(message, wrongFilter, settings);
        Assert.assertNull(result);

        result = MessageComparator.compare(message, correctFilter, settings);
        Assert.assertNotNull(result);
        Assert.assertEquals(2, ComparisonUtil.getResultCount(result, StatusType.PASSED));
        Assert.assertEquals(0, ComparisonUtil.getResultCount(result, StatusType.FAILED));
        Assert.assertTrue("key msg is not a key in 1 result " + result,
                result.getResult("collection")
                        .getResult("1")
                        .getResult("key")
                        .isKey());
    }

    @Test
    public void testSimpleCollectionAsKeyField() {
        IMessage message = createMessage(root -> {
            root.addField("collection", asList(1, 2));
        });

        IMessage correctFilter = createMessage(root -> {
            root.addField("collection", asList(1, 2));
        });

        IMessage wrongFilter = createMessage(root -> {
            root.addField("collection", asList(1, 3));
        });

        ComparatorSettings settings = new ComparatorSettings();
        MetaContainer metaContainer = settings.getMetaContainer();
        metaContainer.setKeyFields(singleton("collection"));

        ComparisonResult result = MessageComparator.compare(message, wrongFilter, settings);
        Assert.assertNull(result);

        result = MessageComparator.compare(message, correctFilter, settings);
        Assert.assertNotNull(result);
        Assert.assertEquals(2, ComparisonUtil.getResultCount(result, StatusType.PASSED));
        Assert.assertEquals(0, ComparisonUtil.getResultCount(result, StatusType.FAILED));
        Assert.assertTrue("collection is not a key in 1 result " + result,
                result.getResult("collection")
                        .isKey());
    }

    private static IMessage createMessage(Consumer<IMessage> initializer) {
        IMessage message = DefaultMessageFactory.getFactory().createMessage("name", "namespace");
        initializer.accept(message);
        return message;
    }

	private enum FilterType {
	    NOT_EMPTY,
	    EMPTY,
	    VALUE
	}
}
