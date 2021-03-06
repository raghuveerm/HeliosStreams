/**
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
 */
package com.heliosapm.streams.metrics.router.nodes;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.kstream.KStreamBuilder;

import com.heliosapm.streams.metrics.router.StreamHubKafkaClientSupplier;

/**
 * <p>Title: MetricStreamNode</p>
 * <p>Description: Represents a node that participates in the metric router</p> 
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.streams.metrics.router.nodes.MetricStreamNode</code></p>
 */

public interface MetricStreamNode {
	
	/**
	 * Callback from the router builder to a participating node.
	 * The node should use the stream builder to configure it's node routing rules.
	 * @param streamBuilder The kafka streams engine builder
	 */
	public void configure(final KStreamBuilder streamBuilder);
	
	/**
	 * Callback from the router builder to a participating node to provide the client supplier
	 * @param clientSupplier The client supplier in case the node needs to do something out of the usual
	 * @param kafkaStreams The instance of the stream engine
	 */
	public void onStart(final StreamHubKafkaClientSupplier clientSupplier, final KafkaStreams kafkaStreams);
	
	/**
	 * Returns the logical name for this node.
	 * Should be unique within the hosting router.
	 * @return the node name
	 */
	public String getName();
	
	/**
	 * Callback from the metic router directing this node to stop and cleanup
	 */
	public void close();
	
	
}
