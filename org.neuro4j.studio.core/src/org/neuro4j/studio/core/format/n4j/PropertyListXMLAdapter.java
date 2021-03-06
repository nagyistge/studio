/*
 * Copyright (c) 2013-2014, Neuro4j.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neuro4j.studio.core.format.n4j;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.adapters.XmlAdapter;

public class PropertyListXMLAdapter extends XmlAdapter<PropertyXML[], List<PropertyXML>> {

    public List<PropertyXML> unmarshal(PropertyXML[] array) {
        List<PropertyXML> propList = new ArrayList<PropertyXML>();
        for (PropertyXML p : array)
            propList.add(p);
        return propList;
    }

    @Override
    public PropertyXML[] marshal(List<PropertyXML> value)
            throws Exception {
        return value.toArray(new PropertyXML[value.size()]);
    }
}