/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.apache.catalina.valves;




/**
 * <p>Implementation of a Valve that tracks Comet connections, and closes them
 * when the associated session expires or the webapp is reloaded.</p>
 *
 * <p>This Valve should be attached to a Context.</p>
 *
 * @author Remy Maucherat
 * @version $Revision: 966 $ $Date: 2009-03-20 18:26:19 +0100 (Fri, 20 Mar 2009) $
 * @deprecated Replaced by EventOrAsyncConnectionManagerValve
 */

public class CometConnectionManagerValve
    extends EventOrAsyncConnectionManagerValve {
}
