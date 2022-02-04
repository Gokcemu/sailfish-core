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
package com.exactpro.sf.common.codecs;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolEncoder;

import com.exactpro.sf.common.messages.IMessageFactory;
import com.exactpro.sf.common.messages.structures.IDictionaryStructure;
import com.exactpro.sf.common.util.ICommonSettings;
import com.exactpro.sf.services.IServiceContext;

public class CodecFactory implements ProtocolCodecFactory {
	
    private final IServiceContext serviceContext;
	private final IMessageFactory msgFactory;
	private final IDictionaryStructure msgDictionary;
	private final Class<? extends AbstractCodec> codecClass;
	private final ICommonSettings codecSettings;
	private final ConcurrentMap<IoSession, AbstractCodec> codecs = new ConcurrentHashMap<>();
    private final Function<AbstractCodec, AbstractCodec> updateCodecFunction;

    public CodecFactory(IServiceContext serviceContext, IMessageFactory msgFactory, IDictionaryStructure dictionary, Class<? extends AbstractCodec> codecClass, ICommonSettings codecSettings, Function<AbstractCodec, AbstractCodec> updateCodecFunction) {
	    
	    this.serviceContext = Objects.requireNonNull(serviceContext, "Service context is not specified");

	    this.msgFactory = Objects.requireNonNull(msgFactory, "Message factory is not specified");

		this.msgDictionary = dictionary;

		this.codecClass = Objects.requireNonNull(codecClass, "Codec class is not specified");

		this.codecSettings = codecSettings;

        this.updateCodecFunction = Objects.requireNonNull(updateCodecFunction, "Update codec function is not specified");
    }

    public CodecFactory(IServiceContext serviceContext, IMessageFactory msgFactory, IDictionaryStructure dictionary, Class<? extends AbstractCodec> codecClass, ICommonSettings codecSettings) {
        this(serviceContext, msgFactory, dictionary, codecClass, codecSettings, Function.identity());
    }

    @Override
    public ProtocolDecoder getDecoder(IoSession session) throws Exception {
        return getCodec(session);
    }

    @Override
    public ProtocolEncoder getEncoder(IoSession session) throws Exception {
        return getCodec(session);
    }

    private AbstractCodec getCodec(IoSession session) throws InstantiationException, IllegalAccessException {
        AbstractCodec codec = codecs.get(session);

        if (codec != null) {
            return codec;
        }

        codec = updateCodecFunction.apply(codecClass.newInstance());
        codec.init(serviceContext, codecSettings, msgFactory, msgDictionary);
        codecs.putIfAbsent(session, codec);
        return codecs.get(session);
    }

}
