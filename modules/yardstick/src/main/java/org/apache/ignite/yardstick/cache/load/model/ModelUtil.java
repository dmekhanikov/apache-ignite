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

package org.apache.ignite.yardstick.cache.load.model;

import org.apache.ignite.yardstick.cache.load.model.key.Identifier;
import org.apache.ignite.yardstick.cache.load.model.key.Mark;
import org.apache.ignite.yardstick.cache.load.model.value.Car;
import org.apache.ignite.yardstick.cache.load.model.value.Color;
import org.apache.ignite.yardstick.cache.load.model.value.Truck;
import org.apache.ignite.yardstick.cache.model.Account;
import org.apache.ignite.yardstick.cache.model.Organization;
import org.apache.ignite.yardstick.cache.model.Person;
import org.apache.ignite.yardstick.cache.model.Person1;
import org.apache.ignite.yardstick.cache.model.Person2;
import org.apache.ignite.yardstick.cache.model.Person8;

import java.util.UUID;

/**
 * Util class for model.
 */
public class ModelUtil {
    /**
     * Classes of keys.
     */
    private static Class[] keyClasses = {
        Double.class,
        Identifier.class,
//        Mark.class,
        Integer.class,
//        UUID.class
    };

    /**
     * Classes of values.
     */
    private static Class[] valueClasses = {
        Car.class,
        Truck.class,
        Person.class,
        Organization.class,
        Account.class,
        Person1.class,
        Person2.class,
        Person8.class
    };

    /**
     * @param clazz Checked class.
     * @return Result.
     */
    public static boolean canCreateInstance(Class clazz) {
        for (Class c: keyClasses) {
            if (c.equals(clazz))
                return true;
        }
        for (Class c: valueClasses) {
            if (c.equals(clazz))
                return true;
        }
        return false;
    }

    /**
     * @param c model class
     * @param id object id
     * @return object from model
     */
    public static Object create(Class c, int id) {

        Object result = null;
        switch (c.getSimpleName()) {
            case "Double":
                result = Double.valueOf(id);
                break;
            case "Identifier":
                result = new Identifier(id, "id " + id);
                break;
            case "Mark":
                result = new Mark(id, UUID.nameUUIDFromBytes(Integer.toString(id).getBytes()));
                break;
            case "Integer":
                result = id;
                break;
            case "UUID":
                result = UUID.nameUUIDFromBytes(Integer.toString(id).getBytes());
                break;
            case "Car":
                int colorCnt = Color.values().length;
                result = new Car(id, "Mark " + id, id/2.123 * 100, Color.values()[id % colorCnt]);
                break;
            case "Truck":
                int colors = Color.values().length;
                result = new Truck(id, "Mark " + id, id/2.123 * 100, Color.values()[id % colors], id/4.123 * 100);
                break;
            case "Person":
                result = new Person(id, "First Name " + id, "Last Name " + id, id/2.123 * 100);
                break;
            case "Organization":
                result = new Organization(id, "Organization " + id);
                break;
            case "Account":
                result = new Account(id);
                break;
            case "Person1":
                result = new Person1(id);
                break;
            case "Person2":
                result = new Person2(id);
                break;
            case "Person8":
                result = new Person8(id);
        }

        return result;
    }

    /**
     * @return array of key cache classes
     */
    public static Class[] keyClasses() {
        return keyClasses;
    }

    /**
     * @return array of value chache classes
     */
    public static Class[] valueClasses() {
        return valueClasses;
    }
}
