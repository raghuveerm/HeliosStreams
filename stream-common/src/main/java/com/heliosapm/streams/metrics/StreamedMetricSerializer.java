/**
 * Helios, OpenSource Monitoring
 * Brought to you by the Helios Development Group
 *
 * Copyright 2016, Helios Development Group and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org. 
 *
 */
package com.heliosapm.streams.metrics;

import java.util.Map;

import org.apache.kafka.common.serialization.Serializer;

/**
 * <p>Title: StreamedMetricSerializer</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.streams.metrics.StreamedMetricSerializer</code></p>
 */

public class StreamedMetricSerializer implements Serializer<StreamedMetric> {


	/**
	 * {@inheritDoc}
	 * @see org.apache.kafka.common.serialization.Serializer#configure(java.util.Map, boolean)
	 */
	@Override
	public void configure(final Map<String, ?> configs, final boolean isKey) {
		/* No Op */
	}

	/**
	 * {@inheritDoc}
	 * @see org.apache.kafka.common.serialization.Serializer#serialize(java.lang.String, java.lang.Object)
	 */
	@Override
	public byte[] serialize(final String topic, final StreamedMetric data) {
		if(data.isValued()) {
			return ((StreamedMetricValue)data).toByteArray();
		}
		return data.toByteArray();
	}

	/**
	 * {@inheritDoc}
	 * @see org.apache.kafka.common.serialization.Serializer#close()
	 */
	@Override
	public void close() {
		/* No Op */
	}

}
