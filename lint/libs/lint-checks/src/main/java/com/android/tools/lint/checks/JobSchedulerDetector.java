/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_PERMISSION;
import static com.android.SdkConstants.CLASS_SERVICE;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_SERVICE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.JavaPsiScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.utils.XmlUtils;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import java.util.Collections;
import java.util.List;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Checks looking for issues related to the JobScheduler API
 */
public class JobSchedulerDetector extends Detector implements JavaPsiScanner {

    @SuppressWarnings("unchecked")
    public static final Implementation IMPLEMENTATION = new Implementation(
            JobSchedulerDetector.class,
            Scope.JAVA_FILE_SCOPE);

    /**
     * Issues that negatively affect battery life
     */
    public static final Issue ISSUE = Issue.create(
            "JobSchedulerService",
            "JobScheduler problems",

            "This check looks for various common mistakes in using the " +
                    "JobScheduler API: the service class must extend `JobService`, " +
                    "the service must be registered in the manifest and the registration " +
                    "must require the permission `android.permission.BIND_JOB_SERVICE`.",

            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            IMPLEMENTATION)
            .addMoreInfo("https://developer.android.com/topic/performance/scheduling.html");

    private static final String CLASS_JOB_SERVICE = "android.app.job.JobService";

    /**
     * Constructs a new {@link JobSchedulerDetector}
     */
    public JobSchedulerDetector() {
    }

    @Nullable
    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList("android.app.job.JobInfo.Builder");
    }

    @Override
    public void visitConstructor(@NonNull JavaContext context, @Nullable JavaElementVisitor visitor,
            @NonNull PsiNewExpression node, @NonNull PsiMethod constructor) {
        PsiExpressionList argumentList = node.getArgumentList();
        if (argumentList == null) {
            return;
        }
        PsiExpression[] arguments = argumentList.getExpressions();
        if (arguments.length < 2) {
            return;
        }
        PsiExpression componentName = arguments[1];
        if (componentName instanceof PsiReferenceExpression) {
            PsiElement resolved = ((PsiReferenceExpression) componentName).resolve();
            if (resolved instanceof PsiVariable) {
                componentName = ConstantEvaluator.findLastAssignment(arguments[1],
                        (PsiVariable) resolved);
            }
        }
        if (!(componentName instanceof PsiNewExpression)) {
            return;
        }
        PsiNewExpression call = (PsiNewExpression) componentName;
        argumentList = call.getArgumentList();
        if (argumentList == null) {
            return;
        }
        arguments = argumentList.getExpressions();
        if (arguments.length < 2) {
            return;
        }
        PsiExpression typeReference = arguments[1];
        if (!(typeReference instanceof PsiClassObjectAccessExpression)) {
            return;
        }
        PsiClassObjectAccessExpression classRef = (PsiClassObjectAccessExpression) typeReference;
        PsiType serviceType = classRef.getOperand().getType();
        if (!(serviceType instanceof PsiClassType)) {
            return;
        }
        PsiClass serviceClass = ((PsiClassType) serviceType).resolve();
        if (serviceClass == null) {
            return;
        }
        JavaEvaluator evaluator = context.getEvaluator();
        if (evaluator.inheritsFrom(serviceClass, CLASS_SERVICE, false)
                && !evaluator.inheritsFrom(serviceClass, CLASS_JOB_SERVICE, false)) {
            String message = String.format("Scheduled job class %1$s must extend "
                    + "android.app.job.JobService", serviceClass.getName());
            context.report(ISSUE, componentName, context.getLocation(componentName), message);
        } else {
            ensureBindServicePermission(context, serviceType.getCanonicalText(), classRef);
        }
    }

    private static void ensureBindServicePermission(
            @NonNull JavaContext context,
            @NonNull String fqcn,
            @NonNull PsiClassObjectAccessExpression typeReference) {
        // Make sure the app has
        //    android:permission="android.permission.BIND_JOB_SERVICE"
        // as well.

        Project project = context.getMainProject();
        Document mergedManifest = project.getMergedManifest();
        if (mergedManifest == null) {
            return;
        }
        Element manifest = mergedManifest.getDocumentElement();
        if (manifest == null) {
            return;
        }
        Element application = XmlUtils.getFirstSubTagTagByName(manifest, TAG_APPLICATION);
        if (application == null) {
            return;
        }

        Element service = XmlUtils.getFirstSubTagTagByName(application, TAG_SERVICE);

        while (service != null) {
            String name = LintUtils.resolveManifestName(service).replace('$', '.');
            if (fqcn.equals(name)) {
                // Check that it has the desired permission
                String permission = service.getAttributeNS(ANDROID_URI, ATTR_PERMISSION);
                if (!"android.permission.BIND_JOB_SERVICE".equals(permission)) {
                    Location location = context.getLocation(typeReference);
                    // Also report the manifest location, if possible
                    LintClient client = context.getClient();
                    Location secondary = client.findManifestSourceLocation(service);
                    if (secondary != null) {
                        location = location.withSecondary(secondary, "Service declaration here",
                                false);
                    }

                    context.report(ISSUE, typeReference, location,
                            "The manifest registration for this service does not declare "
                                + "`android:permission=\"android.permission.BIND_JOB_SERVICE\"`");
                }
                return;
            }

            service = XmlUtils.getNextTagByName(service, TAG_SERVICE);
        }

        // Service not found in the manifest; flag it
        context.report(ISSUE, typeReference, context.getLocation(typeReference),
                "Did not find a manifest registration for this service");
    }
}
