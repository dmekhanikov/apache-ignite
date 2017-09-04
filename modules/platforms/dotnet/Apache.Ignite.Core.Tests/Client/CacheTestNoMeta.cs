﻿/*
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

namespace Apache.Ignite.Core.Tests.Client
{
    using System.Collections.Generic;
    using Apache.Ignite.Core.Binary;
    using Apache.Ignite.Core.Client;
    using Apache.Ignite.Core.Impl.Binary;
    using Apache.Ignite.Core.Impl.Binary.Metadata;
    using NUnit.Framework;

    /// <summary>
    /// Client cache test without metadata (no-op binary processor).
    /// </summary>
    public class CacheTestNoMeta
    {
        /** Cache name. */
        private const string CacheName = "cache";

        /// <summary>
        /// Fixture tear down.
        /// </summary>
        [TestFixtureSetUp]
        public void FixtureSetUp()
        {
            Ignition.Start(TestUtils.GetTestConfiguration());
        }

        /// <summary>
        /// Fixture tear down.
        /// </summary>
        [TestFixtureTearDown]
        public void FixtureTearDown()
        {
            Ignition.StopAll(true);
        }

        /// <summary>
        /// Tests the cache put / get with user data types.
        /// </summary>
        [Test]
        public void TestPutGetUserObjects()
        {
            var cfg = new IgniteClientConfiguration {BinaryProcessor = new NoopBinaryProcessor()};

            using (var client = Ignition.GetClient(cfg))
            {
                var person = new Person { Id = 100, Name = "foo" };
                var person2 = new Person2 { Id = 200, Name = "bar" };

                var serverCache = Ignition.GetIgnite().GetCache<int?, Person>(CacheName);
                var clientCache = client.GetCache<int?, Person>(CacheName);

                // Put through client cache.
                clientCache.Put(1, person);
                clientCache[2] = person2;

                // Read from client cache.
                Assert.AreEqual("foo", clientCache.Get(1).Name);
                Assert.AreEqual(100, clientCache[1].Id);
                Assert.AreEqual(200, clientCache[2].Id);

                // Read from server cache.
                Assert.AreEqual("foo", serverCache.Get(1).Name);
                Assert.AreEqual(100, serverCache[1].Id);
                Assert.AreEqual(200, serverCache[2].Id);
                Assert.AreEqual(200, serverCache[3].Id);

                // SQL from server cache.
                // TODO
            }
        }

        private class NoopBinaryProcessor : IBinaryProcessor
        {
            /** <inheritdoc /> */
            public BinaryType GetBinaryType(int typeId)
            {
                return null;
            }

            /** <inheritdoc /> */
            public List<IBinaryType> GetBinaryTypes()
            {
                return null;
            }

            /** <inheritdoc /> */
            public int[] GetSchema(int typeId, int schemaId)
            {
                return null;
            }

            /** <inheritdoc /> */
            public void PutBinaryTypes(ICollection<BinaryType> types)
            {
                // No-op.
            }

            /** <inheritdoc /> */
            public bool RegisterType(int id, string typeName)
            {
                return false;
            }

            /** <inheritdoc /> */
            public BinaryType RegisterEnum(string typeName, IEnumerable<KeyValuePair<string, int>> values)
            {
                return null;
            }

            /** <inheritdoc /> */
            public string GetTypeName(int id)
            {
                return null;
            }
        }
    }
}