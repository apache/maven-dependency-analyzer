package org.apache.maven.shared.dependency.analyzer;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/**
 * Usage of a dependency class by a project class.
 *
 * @author <a href="mailto:hijon89@gmail.com">Jonathan Haber</a>
 */
public class DependencyUsage
{
  // fields -----------------------------------------------------------------

  private final String dependencyClass;

  private final String usedBy;

  // constructors -----------------------------------------------------------

  public DependencyUsage( String dependencyClass, String usedBy )
  {
    this.dependencyClass = dependencyClass;
    this.usedBy = usedBy;
  }

  // public methods ---------------------------------------------------------


  public String getDependencyClass()
  {
    return dependencyClass;
  }

  public String getUsedBy()
  {
    return usedBy;
  }

  // Object methods ---------------------------------------------------------

  /*
   * @see java.lang.Object#hashCode()
   */
  public int hashCode()
  {
    int hashCode = dependencyClass.hashCode();
    hashCode = ( hashCode * 37 ) + usedBy.hashCode();

    return hashCode;
  }

  /*
   * @see java.lang.Object#equals(java.lang.Object)
   */
  public boolean equals( Object object )
  {
    if ( object instanceof DependencyUsage )
    {
      DependencyUsage usage = (DependencyUsage) object;

      return getDependencyClass().equals( usage.getDependencyClass() )
          && getUsedBy().equals( usage.getUsedBy() );
    }

    return false;
  }

  /*
   * @see java.lang.Object#toString()
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();

    buffer.append( "dependencyClass=" ).append( getDependencyClass() );
    buffer.append( "," );
    buffer.append( "usedBy=" ).append( getUsedBy() );

    buffer.insert( 0, "[" );
    buffer.insert( 0, getClass().getName() );

    buffer.append( "]" );

    return buffer.toString();
  }
}
