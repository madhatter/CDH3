/**
 * Licensed to Cloudera, Inc. under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  Cloudera, Inc. licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cloudera.sqoop.io;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.compress.CompressionCodecFactory;
import org.apache.hadoop.util.ReflectionUtils;

/**
 * Provides a mapping from codec names to concrete implementation class names.
 */
public final class CodecMap {

  // Supported codec map values
  // Note: do not add more values here, since codecs are discovered using the
  // standard Hadoop mechanism (io.compression.codecs). See
  // CompressionCodecFactory.
  public static final String NONE = "none";
  public static final String DEFLATE = "deflate";
  public static final String LZO = "lzo";

  private static Map<String, String> codecNames;
  static {
    codecNames = new TreeMap<String, String>();

    // Register the names of codecs we know about.
    codecNames.put(NONE,    null);
    codecNames.put(DEFLATE, "org.apache.hadoop.io.compress.DefaultCodec");
    codecNames.put(LZO,     "com.hadoop.compression.lzo.LzoCodec");

    // add more from Hadoop CompressionCodecFactory
    for (Class<? extends CompressionCodec> cls
        : CompressionCodecFactory.getCodecClasses(new Configuration())) {
      String simpleName = cls.getSimpleName();
      String codecName = simpleName;
      if (simpleName.endsWith("Codec")) {
        codecName = simpleName.substring(0, simpleName.length()
            - "Codec".length());
      }
      codecNames.put(codecName.toLowerCase(), cls.getCanonicalName());
    }
  }

  private CodecMap() {
  }

  /**
   * Given a codec name, return the name of the concrete class
   * that implements it (or 'null' in the case of the "none" codec).
   * @throws UnsupportedCodecException if a codec cannot be found
   * with the supplied name.
   */
  public static String getCodecClassName(String codecName)
      throws UnsupportedCodecException {
    if (!codecNames.containsKey(codecName)) {
      throw new UnsupportedCodecException(codecName);
    }

    return codecNames.get(codecName);
  }

  /**
   * Given a codec name, instantiate the concrete implementation
   * class that implements it.
   * @throws UnsupportedCodecException if a codec cannot be found
   * with the supplied name.
   */
  public static CompressionCodec getCodec(String codecName,
      Configuration conf) throws UnsupportedCodecException {
    // Try standard Hadoop mechanism first
    CompressionCodec codec = getCodecByName(codecName, conf);
    if (codec != null) {
      return codec;
    }
    // Fall back to Sqoop mechanism
    String codecClassName = null;
    try {
      codecClassName = getCodecClassName(codecName);
      if (null == codecClassName) {
        return null;
      }
      Class<? extends CompressionCodec> codecClass =
          (Class<? extends CompressionCodec>)
          conf.getClassByName(codecClassName);
      return (CompressionCodec) ReflectionUtils.newInstance(
          codecClass, conf);
    } catch (ClassNotFoundException cnfe) {
      throw new UnsupportedCodecException("Cannot find codec class "
          + codecClassName + " for codec " + codecName);
    }
  }

  /**
   * Find the relevant compression codec for the codec's canonical class name
   * or by codec alias.
   * <p>
   * Codec aliases are case insensitive.
   * <p>
   * The code alias is the short class name (without the package name).
   * If the short class name ends with 'Codec', then there are two aliases for
   * the codec, the complete short class name and the short class name without
   * the 'Codec' ending. For example for the 'GzipCodec' codec class name the
   * alias are 'gzip' and 'gzipcodec'.
   * <p>
   * Note: When HADOOP-7323 is available this method can be replaced with a call
   * to CompressionCodecFactory.
   * @param classname the canonical class name of the codec or the codec alias
   * @return the codec object or null if none matching the name were found
   */
  private static CompressionCodec getCodecByName(String codecName,
      Configuration conf) {
    List<Class<? extends CompressionCodec>> codecs =
      CompressionCodecFactory.getCodecClasses(conf);
    for (Class<? extends CompressionCodec> cls : codecs) {
      if (codecMatches(cls, codecName)) {
        return ReflectionUtils.newInstance(cls, conf);
      }
    }
    return null;
  }

  private static boolean codecMatches(Class<? extends CompressionCodec> cls,
      String codecName) {
    String simpleName = cls.getSimpleName();
    if (cls.getName().equals(codecName)
        || simpleName.equalsIgnoreCase(codecName)) {
      return true;
    }
    if (simpleName.endsWith("Codec")) {
      String prefix = simpleName.substring(0, simpleName.length()
          - "Codec".length());
      if (prefix.equalsIgnoreCase(codecName)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Return the set of available codec names.
   */
  public static Set<String> getCodecNames() {
    return codecNames.keySet();
  }
}
