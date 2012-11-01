/**
 *  Copyright 2012 LiveRamp
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.liveramp.megadesk;

import com.google.common.base.Throwables;
import com.liveramp.megadesk.curator.CuratorStep;
import com.liveramp.megadesk.curator.StringCuratorResource;
import com.liveramp.megadesk.resource.Reads;
import com.liveramp.megadesk.resource.Writes;
import com.liveramp.megadesk.step.Step;
import com.liveramp.megadesk.test.BaseTestCase;
import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.CuratorFrameworkFactory;
import com.netflix.curator.retry.RetryNTimes;
import com.netflix.curator.test.TestingServer;

public class IntegrationTest extends BaseTestCase {

  public void testWorkflow() throws Exception {

    TestingServer testingServer = new TestingServer(12000);
    final CuratorFramework curator;
    curator = CuratorFrameworkFactory.builder()
        .connectionTimeoutMs(1000)
        .retryPolicy(new RetryNTimes(10, 500))
        .connectString(testingServer.getConnectString())
        .build();
    curator.start();

    Thread stepZ = new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          StringCuratorResource resourceA = new StringCuratorResource(curator, "resourceA");
          StringCuratorResource resourceB = new StringCuratorResource(curator, "resourceB");
          Step step = new CuratorStep(curator,
              "stepZ",
              Reads.list(resourceA.is("ready")),
              Writes.list(resourceB));
          step.attempt();
          step.setState(resourceB, "ready");
          step.complete();
        } catch (Exception e) {
          throw Throwables.propagate(e);
        }
      }
    }, "stepZ");

    Thread stepA = new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          StringCuratorResource resourceA = new StringCuratorResource(curator, "resourceA");
          StringCuratorResource resourceB = new StringCuratorResource(curator, "resourceB");
          StringCuratorResource resourceC = new StringCuratorResource(curator, "resourceC");
          Step step = new CuratorStep(curator,
              "stepA",
              Reads.list(resourceA.is("ready"), resourceB.is("ready")),
              Writes.list(resourceC));
          step.attempt();
          step.setState(resourceC, "done");
          step.complete();
        } catch (Exception e) {
          throw Throwables.propagate(e);
        }
      }
    }, "stepA");

    Thread stepB = new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          StringCuratorResource resourceC = new StringCuratorResource(curator, "resourceC");
          StringCuratorResource resourceD = new StringCuratorResource(curator, "resourceD");
          Step step = new CuratorStep(curator,
              "stepB",
              Reads.list(resourceC.is("done")),
              Writes.list(resourceD));
          step.attempt();
          step.setState(resourceD, "done");
          step.complete();
        } catch (Exception e) {
          throw Throwables.propagate(e);
        }
      }
    }, "stepB");

    stepA.start();
    stepB.start();
    stepZ.start();

    StringCuratorResource resourceA = new StringCuratorResource(curator, "resourceA");
    StringCuratorResource resourceB = new StringCuratorResource(curator, "resourceB");
    StringCuratorResource resourceC = new StringCuratorResource(curator, "resourceC");
    StringCuratorResource resourceD = new StringCuratorResource(curator, "resourceD");

    Thread.sleep(1000);

    resourceA.setState("ready");

    stepA.join();
    stepB.join();
    stepZ.join();

    assertEquals("ready", resourceA.getState());
    assertEquals("ready", resourceB.getState());
    assertEquals("done", resourceC.getState());
    assertEquals("done", resourceD.getState());
  }
}
