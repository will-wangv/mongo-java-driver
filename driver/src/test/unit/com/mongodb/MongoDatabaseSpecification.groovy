/*
 * Copyright (c) 2008-2014 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb

import com.mongodb.client.MongoCollectionOptions
import com.mongodb.client.MongoDatabaseOptions
import com.mongodb.client.model.CreateCollectionOptions
import com.mongodb.operation.BatchCursor
import com.mongodb.operation.CommandReadOperation
import com.mongodb.operation.CommandWriteOperation
import com.mongodb.operation.CreateCollectionOperation
import com.mongodb.operation.DropDatabaseOperation
import com.mongodb.operation.ListCollectionsOperation
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.Document
import org.bson.codecs.BsonValueCodecProvider
import org.bson.codecs.DocumentCodec
import org.bson.codecs.DocumentCodecProvider
import org.bson.codecs.configuration.RootCodecRegistry
import spock.lang.Specification

import static com.mongodb.CustomMatchers.isTheSameAs
import static com.mongodb.ReadPreference.primary
import static com.mongodb.ReadPreference.primaryPreferred
import static com.mongodb.ReadPreference.secondary
import static com.mongodb.ReadPreference.secondaryPreferred
import static spock.util.matcher.HamcrestSupport.expect

class MongoDatabaseSpecification extends Specification {

    def name = 'databaseName'
    def options = MongoDatabaseOptions.builder().codecRegistry(new RootCodecRegistry([new DocumentCodecProvider(),
                                                                                      new BsonValueCodecProvider()]))
                                      .writeConcern(WriteConcern.ACKNOWLEDGED)
                                      .readPreference(primary())
                                      .build()

    def 'should return the correct name from getName'() {
        given:
        def database = new MongoDatabaseImpl(name, options, new TestOperationExecutor([]))

        expect:
        database.getName() == name
    }

    def 'should return the correct options'() {
        given:
        def database = new MongoDatabaseImpl(name, options, new TestOperationExecutor([]))

        expect:
        database.getOptions() == options
    }

    def 'should be able to executeCommand correctly'() {
        given:
        def command = new BsonDocument('command', new BsonInt32(1))
        def executor = new TestOperationExecutor([null, null, null, null])
        def database = new MongoDatabaseImpl(name, options, executor)

        when:
        database.executeCommand(command)
        def operation = executor.getWriteOperation() as CommandWriteOperation<Document>

        then:
        operation.command == command

        when:
        database.executeCommand(command, primaryPreferred())
        operation = executor.getReadOperation() as CommandReadOperation<Document>

        then:
        operation.command == command
        executor.getReadPreference() == primaryPreferred()

        when:
        database.executeCommand(command, BsonDocument)
        operation = executor.getWriteOperation() as CommandWriteOperation<BsonDocument>

        then:
        operation.command == command

        when:
        database.executeCommand(command, primaryPreferred(), BsonDocument)
        operation = executor.getReadOperation() as CommandReadOperation<BsonDocument>

        then:
        operation.command == command
        executor.getReadPreference() == primaryPreferred()
    }

    def 'should use DropDatabaseOperation correctly'() {
        given:
        def executor = new TestOperationExecutor([null])

        when:
        new MongoDatabaseImpl(name, options, executor).dropDatabase()
        def operation = executor.getWriteOperation() as DropDatabaseOperation

        then:
        expect operation, isTheSameAs(new DropDatabaseOperation(name))
    }

    def 'should use ListCollectionNamesOperation correctly'() {
        given:
        def cursor = Stub(BatchCursor) {
            hasNext() >>> [true, true, false]
            next() >> [new Document('name', 'coll1')]
        }
        def executor = new TestOperationExecutor([cursor])

        when:
        def names = new MongoDatabaseImpl(name, options, executor).getCollectionNames()
        def operation = executor.getReadOperation() as ListCollectionsOperation

        then:
        names == ['coll1']
        expect operation, isTheSameAs(new ListCollectionsOperation(name, new DocumentCodec()))
        executor.getReadPreference() == primary()
    }

    def 'should use CreateCollectionOperation correctly'() {
        given:
        def collectionName = 'collectionName'
        def executor = new TestOperationExecutor([null, null])
        def database = new MongoDatabaseImpl(name, options, executor)

        when:
        database.createCollection(collectionName)
        def operation = executor.getWriteOperation() as CreateCollectionOperation

        then:
        expect operation, isTheSameAs(new CreateCollectionOperation(name, collectionName))

        when:
        def createCollectionOptions = new CreateCollectionOptions()
                .autoIndex(false)
                .capped(true)
                .usePowerOf2Sizes(true)
                .maxDocuments(100)
                .sizeInBytes(1000)
                .storageEngineOptions(new Document('wiredTiger', new Document()))

        database.createCollection(collectionName, createCollectionOptions)
        operation = executor.getWriteOperation() as CreateCollectionOperation

        then:
        expect operation, isTheSameAs(new CreateCollectionOperation(name, collectionName)
                                              .autoIndex(false)
                                              .capped(true)
                                              .usePowerOf2Sizes(true)
                                              .maxDocuments(100)
                                              .sizeInBytes(1000)
                                              .storageEngineOptions(new BsonDocument('wiredTiger', new BsonDocument())))
    }

    def 'should pass the correct options to getCollection'() {
        given:
        def options = MongoDatabaseOptions.builder()
                                      .readPreference(secondary())
                                      .writeConcern(WriteConcern.ACKNOWLEDGED)
                                      .codecRegistry(codecRegistry)
                                      .build()
        def executor = new TestOperationExecutor([])
        def database = new MongoDatabaseImpl(name, options, executor)

        when:
        def collectionOptions = customOptions ? database.getCollection('name', customOptions).getOptions()
                                              : database.getCollection('name').getOptions()
        then:
        collectionOptions.getReadPreference() == readPreference
        collectionOptions.getWriteConcern() == writeConcern
        collectionOptions.getCodecRegistry() == codecRegistry

        where:
        customOptions                                        | readPreference       | writeConcern              | codecRegistry
        null                                                 | secondary()          | WriteConcern.ACKNOWLEDGED | new RootCodecRegistry([])
        MongoCollectionOptions.builder().build()             | secondary()          | WriteConcern.ACKNOWLEDGED | new RootCodecRegistry([])
        MongoCollectionOptions.builder()
                              .readPreference(secondaryPreferred())
                              .writeConcern(WriteConcern.MAJORITY)
                              .build()                       | secondaryPreferred() | WriteConcern.MAJORITY     | new RootCodecRegistry([])

    }

}