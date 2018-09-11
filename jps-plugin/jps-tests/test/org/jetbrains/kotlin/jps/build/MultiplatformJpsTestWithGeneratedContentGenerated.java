/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.jps.build;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.JUnit3RunnerWithInners;
import org.jetbrains.kotlin.test.KotlinTestUtils;
import org.jetbrains.kotlin.test.TargetBackend;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.regex.Pattern;

/** This class is generated by {@link org.jetbrains.kotlin.generators.tests.TestsPackage}. DO NOT MODIFY MANUALLY */
@SuppressWarnings("all")
@TestMetadata("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent")
@TestDataPath("$PROJECT_ROOT")
@RunWith(JUnit3RunnerWithInners.class)
public class MultiplatformJpsTestWithGeneratedContentGenerated extends AbstractMultiplatformJpsTestWithGeneratedContent {
    private void runTest(String testDataFilePath) throws Exception {
        KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
    }

    public void testAllFilesPresentInMultiplatformMultiModule() throws Exception {
        KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent"), Pattern.compile("^([^\\.]+)$"), TargetBackend.ANY, true);
    }

    @TestMetadata("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simple")
    @TestDataPath("$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners.class)
    public static class Simple extends AbstractMultiplatformJpsTestWithGeneratedContent {
        private void runTest(String testDataFilePath) throws Exception {
            KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
        }

        public void testAllFilesPresentInSimple() throws Exception {
            KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simple"), Pattern.compile("^([^\\.]+)$"), TargetBackend.ANY, true);
        }

        @TestMetadata("editingCKotlin")
        public void testEditingCKotlin() throws Exception {
            runTest("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simple/editingCKotlin/");
        }

        @TestMetadata("editingPJsKotlin")
        public void testEditingPJsKotlin() throws Exception {
            runTest("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simple/editingPJsKotlin/");
        }

        @TestMetadata("editingPJvmJava")
        public void testEditingPJvmJava() throws Exception {
            runTest("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simple/editingPJvmJava/");
        }

        @TestMetadata("editingPJvmKotlin")
        public void testEditingPJvmKotlin() throws Exception {
            runTest("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simple/editingPJvmKotlin/");
        }

        @TestMetadata("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simple/editingCKotlin")
        @TestDataPath("$PROJECT_ROOT")
        @RunWith(JUnit3RunnerWithInners.class)
        public static class EditingCKotlin extends AbstractMultiplatformJpsTestWithGeneratedContent {
            private void runTest(String testDataFilePath) throws Exception {
                KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
            }

            public void testAllFilesPresentInEditingCKotlin() throws Exception {
                KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simple/editingCKotlin"), Pattern.compile("^([^\\.]+)$"), TargetBackend.ANY, true);
            }
        }

        @TestMetadata("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simple/editingPJsKotlin")
        @TestDataPath("$PROJECT_ROOT")
        @RunWith(JUnit3RunnerWithInners.class)
        public static class EditingPJsKotlin extends AbstractMultiplatformJpsTestWithGeneratedContent {
            private void runTest(String testDataFilePath) throws Exception {
                KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
            }

            public void testAllFilesPresentInEditingPJsKotlin() throws Exception {
                KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simple/editingPJsKotlin"), Pattern.compile("^([^\\.]+)$"), TargetBackend.ANY, true);
            }
        }

        @TestMetadata("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simple/editingPJvmJava")
        @TestDataPath("$PROJECT_ROOT")
        @RunWith(JUnit3RunnerWithInners.class)
        public static class EditingPJvmJava extends AbstractMultiplatformJpsTestWithGeneratedContent {
            private void runTest(String testDataFilePath) throws Exception {
                KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
            }

            public void testAllFilesPresentInEditingPJvmJava() throws Exception {
                KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simple/editingPJvmJava"), Pattern.compile("^([^\\.]+)$"), TargetBackend.ANY, true);
            }
        }

        @TestMetadata("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simple/editingPJvmKotlin")
        @TestDataPath("$PROJECT_ROOT")
        @RunWith(JUnit3RunnerWithInners.class)
        public static class EditingPJvmKotlin extends AbstractMultiplatformJpsTestWithGeneratedContent {
            private void runTest(String testDataFilePath) throws Exception {
                KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
            }

            public void testAllFilesPresentInEditingPJvmKotlin() throws Exception {
                KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simple/editingPJvmKotlin"), Pattern.compile("^([^\\.]+)$"), TargetBackend.ANY, true);
            }
        }
    }

    @TestMetadata("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simpleJsJvmProjectWithTests")
    @TestDataPath("$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners.class)
    public static class SimpleJsJvmProjectWithTests extends AbstractMultiplatformJpsTestWithGeneratedContent {
        private void runTest(String testDataFilePath) throws Exception {
            KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
        }

        public void testAllFilesPresentInSimpleJsJvmProjectWithTests() throws Exception {
            KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simpleJsJvmProjectWithTests"), Pattern.compile("^([^\\.]+)$"), TargetBackend.ANY, true);
        }

        @TestMetadata("editingCMainExpectActual")
        public void testEditingCMainExpectActual() throws Exception {
            runTest("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simpleJsJvmProjectWithTests/editingCMainExpectActual/");
        }

        @TestMetadata("editingCTestsExpectActual")
        public void testEditingCTestsExpectActual() throws Exception {
            runTest("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simpleJsJvmProjectWithTests/editingCTestsExpectActual/");
        }

        @TestMetadata("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simpleJsJvmProjectWithTests/editingCMainExpectActual")
        @TestDataPath("$PROJECT_ROOT")
        @RunWith(JUnit3RunnerWithInners.class)
        public static class EditingCMainExpectActual extends AbstractMultiplatformJpsTestWithGeneratedContent {
            private void runTest(String testDataFilePath) throws Exception {
                KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
            }

            public void testAllFilesPresentInEditingCMainExpectActual() throws Exception {
                KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simpleJsJvmProjectWithTests/editingCMainExpectActual"), Pattern.compile("^([^\\.]+)$"), TargetBackend.ANY, true);
            }
        }

        @TestMetadata("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simpleJsJvmProjectWithTests/editingCTestsExpectActual")
        @TestDataPath("$PROJECT_ROOT")
        @RunWith(JUnit3RunnerWithInners.class)
        public static class EditingCTestsExpectActual extends AbstractMultiplatformJpsTestWithGeneratedContent {
            private void runTest(String testDataFilePath) throws Exception {
                KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
            }

            public void testAllFilesPresentInEditingCTestsExpectActual() throws Exception {
                KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simpleJsJvmProjectWithTests/editingCTestsExpectActual"), Pattern.compile("^([^\\.]+)$"), TargetBackend.ANY, true);
            }
        }
    }

    @TestMetadata("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simpleNewMpp")
    @TestDataPath("$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners.class)
    public static class SimpleNewMpp extends AbstractMultiplatformJpsTestWithGeneratedContent {
        private void runTest(String testDataFilePath) throws Exception {
            KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
        }

        public void testAllFilesPresentInSimpleNewMpp() throws Exception {
            KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simpleNewMpp"), Pattern.compile("^([^\\.]+)$"), TargetBackend.ANY, true);
        }

        @TestMetadata("editingCKotlin")
        public void testEditingCKotlin() throws Exception {
            runTest("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simpleNewMpp/editingCKotlin/");
        }

        @TestMetadata("editingPJsKotlin")
        public void testEditingPJsKotlin() throws Exception {
            runTest("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simpleNewMpp/editingPJsKotlin/");
        }

        @TestMetadata("editingPJvmJava")
        public void testEditingPJvmJava() throws Exception {
            runTest("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simpleNewMpp/editingPJvmJava/");
        }

        @TestMetadata("editingPJvmKotlin")
        public void testEditingPJvmKotlin() throws Exception {
            runTest("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simpleNewMpp/editingPJvmKotlin/");
        }

        @TestMetadata("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simpleNewMpp/editingCKotlin")
        @TestDataPath("$PROJECT_ROOT")
        @RunWith(JUnit3RunnerWithInners.class)
        public static class EditingCKotlin extends AbstractMultiplatformJpsTestWithGeneratedContent {
            private void runTest(String testDataFilePath) throws Exception {
                KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
            }

            public void testAllFilesPresentInEditingCKotlin() throws Exception {
                KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simpleNewMpp/editingCKotlin"), Pattern.compile("^([^\\.]+)$"), TargetBackend.ANY, true);
            }
        }

        @TestMetadata("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simpleNewMpp/editingPJsKotlin")
        @TestDataPath("$PROJECT_ROOT")
        @RunWith(JUnit3RunnerWithInners.class)
        public static class EditingPJsKotlin extends AbstractMultiplatformJpsTestWithGeneratedContent {
            private void runTest(String testDataFilePath) throws Exception {
                KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
            }

            public void testAllFilesPresentInEditingPJsKotlin() throws Exception {
                KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simpleNewMpp/editingPJsKotlin"), Pattern.compile("^([^\\.]+)$"), TargetBackend.ANY, true);
            }
        }

        @TestMetadata("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simpleNewMpp/editingPJvmJava")
        @TestDataPath("$PROJECT_ROOT")
        @RunWith(JUnit3RunnerWithInners.class)
        public static class EditingPJvmJava extends AbstractMultiplatformJpsTestWithGeneratedContent {
            private void runTest(String testDataFilePath) throws Exception {
                KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
            }

            public void testAllFilesPresentInEditingPJvmJava() throws Exception {
                KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simpleNewMpp/editingPJvmJava"), Pattern.compile("^([^\\.]+)$"), TargetBackend.ANY, true);
            }
        }

        @TestMetadata("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simpleNewMpp/editingPJvmKotlin")
        @TestDataPath("$PROJECT_ROOT")
        @RunWith(JUnit3RunnerWithInners.class)
        public static class EditingPJvmKotlin extends AbstractMultiplatformJpsTestWithGeneratedContent {
            private void runTest(String testDataFilePath) throws Exception {
                KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
            }

            public void testAllFilesPresentInEditingPJvmKotlin() throws Exception {
                KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/simpleNewMpp/editingPJvmKotlin"), Pattern.compile("^([^\\.]+)$"), TargetBackend.ANY, true);
            }
        }
    }

    @TestMetadata("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/ultimate")
    @TestDataPath("$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners.class)
    public static class Ultimate extends AbstractMultiplatformJpsTestWithGeneratedContent {
        private void runTest(String testDataFilePath) throws Exception {
            KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
        }

        public void testAllFilesPresentInUltimate() throws Exception {
            KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/ultimate"), Pattern.compile("^([^\\.]+)$"), TargetBackend.ANY, true);
        }

        @TestMetadata("editingACommonExpectActual")
        public void testEditingACommonExpectActual() throws Exception {
            runTest("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/ultimate/editingACommonExpectActual/");
        }

        @TestMetadata("editingAJsClientKotlin")
        public void testEditingAJsClientKotlin() throws Exception {
            runTest("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/ultimate/editingAJsClientKotlin/");
        }

        @TestMetadata("editingAJvmClientJava")
        public void testEditingAJvmClientJava() throws Exception {
            runTest("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/ultimate/editingAJvmClientJava/");
        }

        @TestMetadata("editingAJvmClientKotlin")
        public void testEditingAJvmClientKotlin() throws Exception {
            runTest("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/ultimate/editingAJvmClientKotlin/");
        }

        @TestMetadata("editingBCommonExpectActual")
        public void testEditingBCommonExpectActual() throws Exception {
            runTest("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/ultimate/editingBCommonExpectActual/");
        }

        @TestMetadata("editingRJsKotlin")
        public void testEditingRJsKotlin() throws Exception {
            runTest("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/ultimate/editingRJsKotlin/");
        }

        @TestMetadata("editingRJvmKotlin")
        public void testEditingRJvmKotlin() throws Exception {
            runTest("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/ultimate/editingRJvmKotlin/");
        }

        @TestMetadata("editingRaJsKotlin")
        public void testEditingRaJsKotlin() throws Exception {
            runTest("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/ultimate/editingRaJsKotlin/");
        }

        @TestMetadata("editingRaJvmKotlin")
        public void testEditingRaJvmKotlin() throws Exception {
            runTest("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/ultimate/editingRaJvmKotlin/");
        }

        @TestMetadata("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/ultimate/editingACommonExpectActual")
        @TestDataPath("$PROJECT_ROOT")
        @RunWith(JUnit3RunnerWithInners.class)
        public static class EditingACommonExpectActual extends AbstractMultiplatformJpsTestWithGeneratedContent {
            private void runTest(String testDataFilePath) throws Exception {
                KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
            }

            public void testAllFilesPresentInEditingACommonExpectActual() throws Exception {
                KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/ultimate/editingACommonExpectActual"), Pattern.compile("^([^\\.]+)$"), TargetBackend.ANY, true);
            }
        }

        @TestMetadata("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/ultimate/editingAJsClientKotlin")
        @TestDataPath("$PROJECT_ROOT")
        @RunWith(JUnit3RunnerWithInners.class)
        public static class EditingAJsClientKotlin extends AbstractMultiplatformJpsTestWithGeneratedContent {
            private void runTest(String testDataFilePath) throws Exception {
                KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
            }

            public void testAllFilesPresentInEditingAJsClientKotlin() throws Exception {
                KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/ultimate/editingAJsClientKotlin"), Pattern.compile("^([^\\.]+)$"), TargetBackend.ANY, true);
            }
        }

        @TestMetadata("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/ultimate/editingAJvmClientJava")
        @TestDataPath("$PROJECT_ROOT")
        @RunWith(JUnit3RunnerWithInners.class)
        public static class EditingAJvmClientJava extends AbstractMultiplatformJpsTestWithGeneratedContent {
            private void runTest(String testDataFilePath) throws Exception {
                KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
            }

            public void testAllFilesPresentInEditingAJvmClientJava() throws Exception {
                KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/ultimate/editingAJvmClientJava"), Pattern.compile("^([^\\.]+)$"), TargetBackend.ANY, true);
            }
        }

        @TestMetadata("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/ultimate/editingAJvmClientKotlin")
        @TestDataPath("$PROJECT_ROOT")
        @RunWith(JUnit3RunnerWithInners.class)
        public static class EditingAJvmClientKotlin extends AbstractMultiplatformJpsTestWithGeneratedContent {
            private void runTest(String testDataFilePath) throws Exception {
                KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
            }

            public void testAllFilesPresentInEditingAJvmClientKotlin() throws Exception {
                KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/ultimate/editingAJvmClientKotlin"), Pattern.compile("^([^\\.]+)$"), TargetBackend.ANY, true);
            }
        }

        @TestMetadata("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/ultimate/editingBCommonExpectActual")
        @TestDataPath("$PROJECT_ROOT")
        @RunWith(JUnit3RunnerWithInners.class)
        public static class EditingBCommonExpectActual extends AbstractMultiplatformJpsTestWithGeneratedContent {
            private void runTest(String testDataFilePath) throws Exception {
                KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
            }

            public void testAllFilesPresentInEditingBCommonExpectActual() throws Exception {
                KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/ultimate/editingBCommonExpectActual"), Pattern.compile("^([^\\.]+)$"), TargetBackend.ANY, true);
            }
        }

        @TestMetadata("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/ultimate/editingRJsKotlin")
        @TestDataPath("$PROJECT_ROOT")
        @RunWith(JUnit3RunnerWithInners.class)
        public static class EditingRJsKotlin extends AbstractMultiplatformJpsTestWithGeneratedContent {
            private void runTest(String testDataFilePath) throws Exception {
                KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
            }

            public void testAllFilesPresentInEditingRJsKotlin() throws Exception {
                KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/ultimate/editingRJsKotlin"), Pattern.compile("^([^\\.]+)$"), TargetBackend.ANY, true);
            }
        }

        @TestMetadata("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/ultimate/editingRJvmKotlin")
        @TestDataPath("$PROJECT_ROOT")
        @RunWith(JUnit3RunnerWithInners.class)
        public static class EditingRJvmKotlin extends AbstractMultiplatformJpsTestWithGeneratedContent {
            private void runTest(String testDataFilePath) throws Exception {
                KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
            }

            public void testAllFilesPresentInEditingRJvmKotlin() throws Exception {
                KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/ultimate/editingRJvmKotlin"), Pattern.compile("^([^\\.]+)$"), TargetBackend.ANY, true);
            }
        }

        @TestMetadata("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/ultimate/editingRaJsKotlin")
        @TestDataPath("$PROJECT_ROOT")
        @RunWith(JUnit3RunnerWithInners.class)
        public static class EditingRaJsKotlin extends AbstractMultiplatformJpsTestWithGeneratedContent {
            private void runTest(String testDataFilePath) throws Exception {
                KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
            }

            public void testAllFilesPresentInEditingRaJsKotlin() throws Exception {
                KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/ultimate/editingRaJsKotlin"), Pattern.compile("^([^\\.]+)$"), TargetBackend.ANY, true);
            }
        }

        @TestMetadata("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/ultimate/editingRaJvmKotlin")
        @TestDataPath("$PROJECT_ROOT")
        @RunWith(JUnit3RunnerWithInners.class)
        public static class EditingRaJvmKotlin extends AbstractMultiplatformJpsTestWithGeneratedContent {
            private void runTest(String testDataFilePath) throws Exception {
                KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
            }

            public void testAllFilesPresentInEditingRaJvmKotlin() throws Exception {
                KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("jps-plugin/testData/incremental/multiModule/multiplatform/withGeneratedContent/ultimate/editingRaJvmKotlin"), Pattern.compile("^([^\\.]+)$"), TargetBackend.ANY, true);
            }
        }
    }
}