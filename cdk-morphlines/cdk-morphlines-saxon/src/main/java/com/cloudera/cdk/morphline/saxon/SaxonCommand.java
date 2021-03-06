/*
 * Copyright 2013 Cloudera Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cloudera.cdk.morphline.saxon;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.TransformerException;

import net.sf.saxon.s9api.BuildingStreamWriterImpl;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;

import com.cloudera.cdk.morphline.api.Command;
import com.cloudera.cdk.morphline.api.MorphlineContext;
import com.cloudera.cdk.morphline.api.MorphlineRuntimeException;
import com.cloudera.cdk.morphline.api.Record;
import com.cloudera.cdk.morphline.stdio.AbstractParser;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/** Base class for XQuery and XSLT */
abstract class SaxonCommand extends AbstractParser {
    
  protected final XMLInputFactory inputFactory = new XMLInputFactoryCreator().getXMLInputFactory();
  protected final DocumentBuilder documentBuilder;
  protected final Processor processor;
  protected final boolean isTracing;

  public SaxonCommand(Config config, Command parent, Command child, MorphlineContext context) {
    super(config, parent, child, context);
    
    this.isTracing = getConfigs().getBoolean(config, "isTracing", false);
    boolean licensedSaxonEdition = getConfigs().getBoolean(config, "licensedSaxonEdition", false);
    this.processor = new Processor(licensedSaxonEdition);
    this.documentBuilder = processor.newDocumentBuilder();
    
    Config features = getConfigs().getConfig(config, "features", ConfigFactory.empty());
    for (Map.Entry<String, Object> entry : features.root().unwrapped().entrySet()) {
      processor.setConfigurationProperty(entry.getKey().toString(), entry.getValue());
    }
  }

  @Override
  protected final boolean doProcess(Record inputRecord, InputStream stream) throws IOException {
    try {
      return doProcess2(inputRecord, stream);
    } catch (SaxonApiException e) {
      throw new MorphlineRuntimeException(e);
    } catch (XMLStreamException e) {
      throw new MorphlineRuntimeException(e);
    }
  }
  
  abstract protected boolean doProcess2(Record inputRecord, InputStream stream) throws IOException, SaxonApiException, XMLStreamException;
  
  protected XdmNode parseXmlDocument(File file) throws XMLStreamException, SaxonApiException, IOException {
    InputStream stream = new FileInputStream(file);
    try {
      if (file.getName().endsWith(".gz")) {
        stream = new GZIPInputStream(new BufferedInputStream(stream));
      }
      return parseXmlDocument(stream);
    } finally {
      stream.close();
    }
  }
  
  protected XdmNode parseXmlDocument(InputStream stream) throws XMLStreamException, SaxonApiException {
    XMLStreamReader reader = inputFactory.createXMLStreamReader(null, stream);
    BuildingStreamWriterImpl writer = documentBuilder.newBuildingStreamWriter();      
    new XMLStreamCopier(reader, writer).copy(false); // push XML into Saxon and build TinyTree
    reader.close();
    writer.close();
    XdmNode document = writer.getDocumentNode();
    return document;
  }
  
  
  ///////////////////////////////////////////////////////////////////////////////
  // Nested classes:
  ///////////////////////////////////////////////////////////////////////////////    
  final class DefaultErrorListener implements ErrorListener {

    public void error(TransformerException e) throws TransformerException {
      LOG.error("Error: " + e.getMessageAndLocation(), e);
      throw e;
    }
    
    public void fatalError(TransformerException e) throws TransformerException {
      LOG.error("Fatal error: " + e.getMessageAndLocation(), e);
      throw e;
    }

    public void warning(TransformerException e) throws TransformerException {
      LOG.warn("Warning: " + e.getMessageAndLocation(), e);
    }
  }

}