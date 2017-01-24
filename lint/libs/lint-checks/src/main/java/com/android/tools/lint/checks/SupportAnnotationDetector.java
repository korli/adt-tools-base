/*
 * Copyright (C) 2015 The Android Open Source Project
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
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_VALUE;
import static com.android.SdkConstants.CLASS_INTENT;
import static com.android.SdkConstants.CLASS_VIEW;
import static com.android.SdkConstants.INT_DEF_ANNOTATION;
import static com.android.SdkConstants.STRING_DEF_ANNOTATION;
import static com.android.SdkConstants.SUPPORT_ANNOTATIONS_PREFIX;
import static com.android.SdkConstants.TAG_PERMISSION;
import static com.android.SdkConstants.TAG_USES_PERMISSION;
import static com.android.SdkConstants.TAG_USES_PERMISSION_SDK_23;
import static com.android.SdkConstants.TAG_USES_PERMISSION_SDK_M;
import static com.android.SdkConstants.TYPE_DEF_FLAG_ATTRIBUTE;
import static com.android.resources.ResourceType.COLOR;
import static com.android.resources.ResourceType.DIMEN;
import static com.android.resources.ResourceType.DRAWABLE;
import static com.android.resources.ResourceType.MIPMAP;
import static com.android.tools.lint.checks.PermissionFinder.Operation.ACTION;
import static com.android.tools.lint.checks.PermissionFinder.Operation.READ;
import static com.android.tools.lint.checks.PermissionFinder.Operation.WRITE;
import static com.android.tools.lint.checks.PermissionRequirement.ATTR_PROTECTION_LEVEL;
import static com.android.tools.lint.checks.PermissionRequirement.VALUE_DANGEROUS;
import static com.android.tools.lint.checks.PermissionRequirement.getAnnotationBooleanValue;
import static com.android.tools.lint.checks.PermissionRequirement.getAnnotationDoubleValue;
import static com.android.tools.lint.checks.PermissionRequirement.getAnnotationLongValue;
import static com.android.tools.lint.checks.PermissionRequirement.getAnnotationStringValue;
import static com.android.tools.lint.detector.api.LintUtils.skipParentheses;
import static com.android.tools.lint.detector.api.ResourceEvaluator.ANIMATOR_RES_ANNOTATION;
import static com.android.tools.lint.detector.api.ResourceEvaluator.ANIM_RES_ANNOTATION;
import static com.android.tools.lint.detector.api.ResourceEvaluator.ANY_RES_ANNOTATION;
import static com.android.tools.lint.detector.api.ResourceEvaluator.ARRAY_RES_ANNOTATION;
import static com.android.tools.lint.detector.api.ResourceEvaluator.ATTR_RES_ANNOTATION;
import static com.android.tools.lint.detector.api.ResourceEvaluator.BOOL_RES_ANNOTATION;
import static com.android.tools.lint.detector.api.ResourceEvaluator.COLOR_INT_ANNOTATION;
import static com.android.tools.lint.detector.api.ResourceEvaluator.COLOR_RES_ANNOTATION;
import static com.android.tools.lint.detector.api.ResourceEvaluator.DIMENSION_ANNOTATION;
import static com.android.tools.lint.detector.api.ResourceEvaluator.DIMEN_RES_ANNOTATION;
import static com.android.tools.lint.detector.api.ResourceEvaluator.DRAWABLE_RES_ANNOTATION;
import static com.android.tools.lint.detector.api.ResourceEvaluator.FRACTION_RES_ANNOTATION;
import static com.android.tools.lint.detector.api.ResourceEvaluator.ID_RES_ANNOTATION;
import static com.android.tools.lint.detector.api.ResourceEvaluator.INTEGER_RES_ANNOTATION;
import static com.android.tools.lint.detector.api.ResourceEvaluator.INTERPOLATOR_RES_ANNOTATION;
import static com.android.tools.lint.detector.api.ResourceEvaluator.LAYOUT_RES_ANNOTATION;
import static com.android.tools.lint.detector.api.ResourceEvaluator.MENU_RES_ANNOTATION;
import static com.android.tools.lint.detector.api.ResourceEvaluator.PLURALS_RES_ANNOTATION;
import static com.android.tools.lint.detector.api.ResourceEvaluator.PX_ANNOTATION;
import static com.android.tools.lint.detector.api.ResourceEvaluator.RAW_RES_ANNOTATION;
import static com.android.tools.lint.detector.api.ResourceEvaluator.STRING_RES_ANNOTATION;
import static com.android.tools.lint.detector.api.ResourceEvaluator.STYLEABLE_RES_ANNOTATION;
import static com.android.tools.lint.detector.api.ResourceEvaluator.STYLE_RES_ANNOTATION;
import static com.android.tools.lint.detector.api.ResourceEvaluator.TRANSITION_RES_ANNOTATION;
import static com.android.tools.lint.detector.api.ResourceEvaluator.XML_RES_ANNOTATION;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.MavenCoordinates;
import com.android.resources.ResourceType;
import com.android.sdklib.AndroidVersion;
import com.android.tools.lint.checks.PermissionFinder.Operation;
import com.android.tools.lint.checks.PermissionFinder.Result;
import com.android.tools.lint.checks.PermissionHolder.SetPermissionLookup;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.CharSequences;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.JavaPsiScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiArrayAccessExpression;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiCall;
import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiDisjunctionType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiLiteral;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Looks up annotations on method calls and enforces the various things they
 * express, e.g. for {@code @CheckReturn} it makes sure the return value is used,
 * for {@code ColorInt} it ensures that a proper color integer is passed in, etc.
 */
@SuppressWarnings("WeakerAccess")
public class SupportAnnotationDetector extends Detector implements JavaPsiScanner {

    public static final Implementation IMPLEMENTATION
            = new Implementation(SupportAnnotationDetector.class, Scope.JAVA_FILE_SCOPE);

    /** Method result should be used */
    public static final Issue RANGE = Issue.create(
        "Range",
        "Outside Range",

        "Some parameters are required to in a particular numerical range; this check " +
        "makes sure that arguments passed fall within the range. For arrays, Strings " +
        "and collections this refers to the size or length.",

        Category.CORRECTNESS,
        6,
        Severity.ERROR,
        IMPLEMENTATION);

    /**
     * Attempting to set a resource id as a color
     */
    public static final Issue RESOURCE_TYPE = Issue.create(
        "ResourceType",
        "Wrong Resource Type",

        "Ensures that resource id's passed to APIs are of the right type; for example, " +
        "calling `Resources.getColor(R.string.name)` is wrong.",

        Category.CORRECTNESS,
        7,
        Severity.FATAL,
        IMPLEMENTATION);

    /** Attempting to set a resource id as a color */
    public static final Issue COLOR_USAGE = Issue.create(
        "ResourceAsColor",
        "Should pass resolved color instead of resource id",

        "Methods that take a color in the form of an integer should be passed " +
        "an RGB triple, not the actual color resource id. You must call " +
        "`getResources().getColor(resource)` to resolve the actual color value first.",

        Category.CORRECTNESS,
        7,
        Severity.ERROR,
        IMPLEMENTATION);

    /** Passing the wrong constant to an int or String method */
    public static final Issue TYPE_DEF = Issue.create(
        "WrongConstant",
        "Incorrect constant",

        "Ensures that when parameter in a method only allows a specific set " +
        "of constants, calls obey those rules.",

        Category.SECURITY,
        6,
        Severity.ERROR,
        IMPLEMENTATION);

    /** Using a restricted API */
    public static final Issue RESTRICTED = Issue.create(
            "RestrictedApi",
            "Restricted API",

            "This API has been flagged with a restriction that has not been met.\n" +
            "\n" +
            "Examples of API restrictions:\n" +
            "* Method can only be invoked by a subclass\n" +
            "* Method can only be accessed from within the same library (defined by " +
            " the Gradle library group id)\n." +
            "* Method can only be accessed from tests.\n." +
            "\n" +
            "You can add your own API restrictions with the `@RestrictTo` annotation.",

            Category.CORRECTNESS,
            4,
            Severity.ERROR,
            IMPLEMENTATION);

    /** Using an intended-for-tests API */
    public static final Issue TEST_VISIBILITY = Issue.create(
            "VisibleForTests",
            "Visible Only For Tests",

            "With the `@VisibleForTesting` annotation you can specify an `otherwise=` " +
            "attribute which specifies the intended visibility if the method had not " +
            "been made more widely visible for the tests.\n" +
            "\n" +
            "This check looks for accesses from production code (e.g. not tests) where " +
            "the access would not have been allowed with the intended production visibility.",

            Category.CORRECTNESS,
            4,
            Severity.WARNING,
            IMPLEMENTATION);

    /** Method result should be used */
    public static final Issue CHECK_RESULT = Issue.create(
        "CheckResult",
        "Ignoring results",

        "Some methods have no side effects, an calling them without doing something " +
        "without the result is suspicious. ",

        Category.CORRECTNESS,
        6,
        Severity.WARNING,
            IMPLEMENTATION);

    /** Failing to enforce security by just calling check permission */
    public static final Issue CHECK_PERMISSION = Issue.create(
        "UseCheckPermission",
        "Using the result of check permission calls",

        "You normally want to use the result of checking a permission; these methods " +
        "return whether the permission is held; they do not throw an error if the permission " +
        "is not granted. Code which does not do anything with the return value probably " +
        "meant to be calling the enforce methods instead, e.g. rather than " +
        "`Context#checkCallingPermission` it should call `Context#enforceCallingPermission`.",

        Category.SECURITY,
        6,
        Severity.WARNING,
        IMPLEMENTATION);

    /** Method result should be used */
    public static final Issue MISSING_PERMISSION = Issue.create(
            "MissingPermission",
            "Missing Permissions",

            "This check scans through your code and libraries and looks at the APIs being used, " +
            "and checks this against the set of permissions required to access those APIs. If " +
            "the code using those APIs is called at runtime, then the program will crash.\n" +
            "\n" +
            "Furthermore, for permissions that are revocable (with targetSdkVersion 23), client " +
            "code must also be prepared to handle the calls throwing an exception if the user " +
            "rejects the request for permission at runtime.",

            Category.CORRECTNESS,
            9,
            Severity.ERROR,
            IMPLEMENTATION);

    /** Passing the wrong constant to an int or String method */
    public static final Issue THREAD = Issue.create(
            "WrongThread",
            "Wrong Thread",

            "Ensures that a method which expects to be called on a specific thread, is actually " +
            "called from that thread. For example, calls on methods in widgets should always " +
            "be made on the UI thread.",

            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            IMPLEMENTATION)
            .addMoreInfo(
                    "http://developer.android.com/guide/components/processes-and-threads.html#Threads");

    public static final String CHECK_RESULT_ANNOTATION = SUPPORT_ANNOTATIONS_PREFIX + "CheckResult";
    public static final String INT_RANGE_ANNOTATION = SUPPORT_ANNOTATIONS_PREFIX + "IntRange";
    public static final String FLOAT_RANGE_ANNOTATION = SUPPORT_ANNOTATIONS_PREFIX + "FloatRange";
    public static final String SIZE_ANNOTATION = SUPPORT_ANNOTATIONS_PREFIX + "Size";
    public static final String PERMISSION_ANNOTATION = SUPPORT_ANNOTATIONS_PREFIX + "RequiresPermission";
    public static final String UI_THREAD_ANNOTATION = SUPPORT_ANNOTATIONS_PREFIX + "UiThread";
    public static final String MAIN_THREAD_ANNOTATION = SUPPORT_ANNOTATIONS_PREFIX + "MainThread";
    public static final String WORKER_THREAD_ANNOTATION = SUPPORT_ANNOTATIONS_PREFIX + "WorkerThread";
    public static final String BINDER_THREAD_ANNOTATION = SUPPORT_ANNOTATIONS_PREFIX + "BinderThread";
    public static final String ANY_THREAD_ANNOTATION = SUPPORT_ANNOTATIONS_PREFIX + "AnyThread";
    public static final String RESTRICT_TO_ANNOTATION = SUPPORT_ANNOTATIONS_PREFIX + "RestrictTo";
    public static final String VISIBLE_FOR_TESTING_ANNOTATION = SUPPORT_ANNOTATIONS_PREFIX + "VisibleForTesting";
    public static final String PERMISSION_ANNOTATION_READ = PERMISSION_ANNOTATION + ".Read";
    public static final String PERMISSION_ANNOTATION_WRITE = PERMISSION_ANNOTATION + ".Write";

    // TODO: Add analysis to enforce this annotation:
    public static final String HALF_FLOAT_ANNOTATION = "android.support.annotation.HalfFloat";

    public static final String THREAD_SUFFIX = "Thread";
    public static final String ATTR_SUGGEST = "suggest";
    public static final String ATTR_TO = "to";
    public static final String ATTR_FROM = "from";
    public static final String ATTR_FROM_INCLUSIVE = "fromInclusive";
    public static final String ATTR_TO_INCLUSIVE = "toInclusive";
    public static final String ATTR_MULTIPLE = "multiple";
    public static final String ATTR_MIN = "min";
    public static final String ATTR_MAX = "max";
    public static final String ATTR_ALL_OF = "allOf";
    public static final String ATTR_ANY_OF = "anyOf";
    public static final String ATTR_CONDITIONAL = "conditional";
    public static final String ATTR_OTHERWISE = "otherwise";

    /**
     * Constructs a new {@link SupportAnnotationDetector} check
     */
    public SupportAnnotationDetector() {
    }

    private static void report(
            @NonNull JavaContext context,
            @NonNull Issue issue,
            @Nullable PsiElement scope,
            @NonNull Location location,
            @NonNull String message) {
        report(context, issue, scope, location, message, null);
    }

    private static void report(
            @NonNull JavaContext context,
            @NonNull Issue issue,
            @Nullable PsiElement scope,
            @NonNull Location location,
            @NonNull String message,
            @Nullable Object quickfixData) {
        // In the IDE historically (until 2.0) many checks were covered by the
        // ResourceTypeInspection, and when suppressed, these would all be suppressed with the
        // id "ResourceType".
        //
        // Since then I've split this up into multiple separate issues, but we still want
        // to honor the older suppress id, so explicitly check for it here:
        LintDriver driver = context.getDriver();
        if (issue != RESOURCE_TYPE) {
            if (scope != null && driver.isSuppressed(context, RESOURCE_TYPE, scope)) {
                return;
            }
        }

        if (scope != null && driver.isSuppressed(context, issue, scope)) {
            return;
        }

        context.report(issue, location, message, quickfixData);
    }

    private void checkContextAnnotations(@NonNull JavaContext context, @Nullable PsiMethod method,
            @NonNull PsiElement call,
            @NonNull PsiAnnotation[] allMethodAnnotations) {
        // Handle typedefs and resource types: if you're comparing it, check that
        // it's being compared with something compatible
        PsiElement p = skipParentheses(call.getParent());
        if (p instanceof PsiBinaryExpression) {
            PsiExpression check = null;
            PsiBinaryExpression binary = (PsiBinaryExpression) p;
            if (call == binary.getLOperand()) {
                check = binary.getROperand();
            } else if (call == binary.getROperand()) {
                check = binary.getLOperand();
            }
            if (check != null) {
                checkAnnotations(context, check, check, method, allMethodAnnotations,
                        PsiAnnotation.EMPTY_ARRAY, PsiAnnotation.EMPTY_ARRAY);
            }
        } else if (p instanceof PsiReferenceExpression
                && skipParentheses(p.getParent()) instanceof PsiMethodCallExpression) {
            // Handle equals() as a special case: if you're invoking
            //   .equals on a method whose return value annotated with @StringDef
            //   we want to make sure that the equals parameter is compatible.
            // 186598: StringDef don't warn using a getter and equals
            PsiReferenceExpression ref = (PsiReferenceExpression)p;
            if ("equals".equals(ref.getReferenceName())) {
                PsiMethodCallExpression parent =
                        (PsiMethodCallExpression) skipParentheses(ref.getParent());
                assert parent != null;
                PsiExpression[] arguments = parent.getArgumentList().getExpressions();
                if (arguments.length == 1) {
                    checkAnnotations(context, arguments[0], arguments[0], method,
                            allMethodAnnotations,
                            PsiAnnotation.EMPTY_ARRAY, PsiAnnotation.EMPTY_ARRAY);
                }
            }
        } else if (p instanceof PsiAssignmentExpression) {
            PsiAssignmentExpression assignment = (PsiAssignmentExpression)p;
            PsiExpression rExpression = assignment.getRExpression();
            if (rExpression != null) {
                checkAnnotations(context, rExpression, rExpression, method,
                        allMethodAnnotations, PsiAnnotation.EMPTY_ARRAY, PsiAnnotation.EMPTY_ARRAY);
            }
        }
    }

    private void checkAnnotations(
            @NonNull JavaContext context,
            @NonNull PsiElement argument,
            @NonNull PsiElement call,
            @Nullable PsiMethod method,
            @NonNull PsiAnnotation[] annotations,
            @NonNull PsiAnnotation[] allMethodAnnotations,
            @NonNull PsiAnnotation[] allClassAnnotations) {
        boolean handledResourceTypes = false;
        for (PsiAnnotation annotation : annotations) {
            String signature = annotation.getQualifiedName();
            if (signature == null) {
                continue;
            }

            switch (signature) {
                case COLOR_INT_ANNOTATION:
                    checkColor(context, argument);
                    break;
                case DIMENSION_ANNOTATION:
                case PX_ANNOTATION:
                    checkPx(context, argument);
                    break;
                case INT_RANGE_ANNOTATION:
                    checkIntRange(context, annotation, argument, annotations);
                    break;
                case FLOAT_RANGE_ANNOTATION:
                    checkFloatRange(context, annotation, argument);
                    break;
                case SIZE_ANNOTATION:
                    checkSize(context, annotation, argument);
                    break;
                case PERMISSION_ANNOTATION:
                case PERMISSION_ANNOTATION_READ:
                case PERMISSION_ANNOTATION_WRITE:
                    if (annotations == allMethodAnnotations) {
                        // Permission annotation specified on method:
                        PermissionRequirement requirement = PermissionRequirement.create(annotation);
                        checkPermission(context, call, method, null, requirement);
                    } else {
                        // PERMISSION_ANNOTATION, PERMISSION_ANNOTATION_READ, PERMISSION_ANNOTATION_WRITE
                        // When specified on a parameter, that indicates that we're dealing with
                        // a permission requirement on this *method* which depends on the value
                        // supplied by this parameter
                        if (argument instanceof PsiExpression && method != null) {
                            checkParameterPermission(context, signature, call, method,
                                    (PsiExpression) argument);
                        }
                    }
                    break;
                case INT_DEF_ANNOTATION: {
                    boolean flag = getAnnotationBooleanValue(annotation, TYPE_DEF_FLAG_ATTRIBUTE)
                            == Boolean.TRUE;
                    checkTypeDefConstant(context, annotation, argument, null, flag,
                            annotations);
                    break;
                }
                case STRING_DEF_ANNOTATION:
                    checkTypeDefConstant(context, annotation, argument, null, false,
                        annotations);
                    break;

                // Resource types
                case ANIMATOR_RES_ANNOTATION:
                case ANIM_RES_ANNOTATION:
                case ANY_RES_ANNOTATION:
                case ARRAY_RES_ANNOTATION:
                case ATTR_RES_ANNOTATION:
                case BOOL_RES_ANNOTATION:
                case COLOR_RES_ANNOTATION:
                case DIMEN_RES_ANNOTATION:
                case DRAWABLE_RES_ANNOTATION:
                case FRACTION_RES_ANNOTATION:
                case ID_RES_ANNOTATION:
                case INTEGER_RES_ANNOTATION:
                case INTERPOLATOR_RES_ANNOTATION:
                case LAYOUT_RES_ANNOTATION:
                case MENU_RES_ANNOTATION:
                case PLURALS_RES_ANNOTATION:
                case RAW_RES_ANNOTATION:
                case STRING_RES_ANNOTATION:
                case STYLEABLE_RES_ANNOTATION:
                case STYLE_RES_ANNOTATION:
                case TRANSITION_RES_ANNOTATION:
                case XML_RES_ANNOTATION:
                    if (handledResourceTypes) {
                        continue;
                    }
                    handledResourceTypes = true;
                    EnumSet<ResourceType> types = ResourceEvaluator.getTypesFromAnnotations(annotations);
                    if (types != null) {
                        checkResourceType(context, argument, types, call, method);
                    }
                    break;

                case CHECK_RESULT_ANNOTATION:
                case "edu.umd.cs.findbugs.annotations.CheckReturnValue":
                case "javax.annotation.CheckReturnValue":
                case "com.google.errorprone.annotations.CanIgnoreReturnValue":
                    if (method != null) {
                        checkResult(context, call, method, annotation);
                    }
                    break;

                case UI_THREAD_ANNOTATION:
                case MAIN_THREAD_ANNOTATION:
                case BINDER_THREAD_ANNOTATION:
                case WORKER_THREAD_ANNOTATION:
                case ANY_THREAD_ANNOTATION:
                    if (method != null) {
                        checkThreading(context, call, method, signature, annotation,
                                allMethodAnnotations,
                                allClassAnnotations);
                    }
                    break;

                case RESTRICT_TO_ANNOTATION:
                    if (method != null) {
                        checkRestrictTo(context, call, method, annotation, allMethodAnnotations,
                                allClassAnnotations);
                    }
                    break;
                case VISIBLE_FOR_TESTING_ANNOTATION:
                    if (method != null) {
                        checkVisibleForTesting(context, call, method, annotation, allMethodAnnotations,
                                allClassAnnotations);
                    }
                    break;
            }
        }
    }

    private void checkParameterPermission(
            @NonNull JavaContext context,
            @NonNull String signature,
            @NonNull PsiElement call,
            @NonNull PsiMethod method,
            @NonNull PsiExpression argument) {
        Operation operation = null;
        //noinspection IfCanBeSwitch
        if (signature.equals(PERMISSION_ANNOTATION_READ)) {
            operation = READ;
        } else if (signature.equals(PERMISSION_ANNOTATION_WRITE)) {
            operation = WRITE;
        } else {
            PsiType type = argument.getType();
            if (type != null && CLASS_INTENT.equals(type.getCanonicalText())) {
                operation = ACTION;
            }
        }
        if (operation == null) {
            return;
        }
        Result result = PermissionFinder.findRequiredPermissions(operation, argument);
        if (result != null) {
            checkPermission(context, call, method, result, result.requirement);
        }
    }

    private static void checkColor(@NonNull JavaContext context, @NonNull PsiElement argument) {
        if (argument instanceof PsiConditionalExpression) {
            PsiConditionalExpression expression = (PsiConditionalExpression) argument;
            if (expression.getThenExpression() != null) {
                checkColor(context, expression.getThenExpression());
            }
            if (expression.getElseExpression() != null) {
                checkColor(context, expression.getElseExpression());
            }
            return;
        }

        EnumSet<ResourceType> types = ResourceEvaluator.getResourceTypes(context.getEvaluator(),
                argument);

        if (types != null && types.contains(COLOR)) {
            String message = String.format(
                    "Should pass resolved color instead of resource id here: " +
                            "`getResources().getColor(%1$s)`", argument.getText());
            report(context, COLOR_USAGE, argument, context.getLocation(argument), message);
        }
    }

    private static void checkPx(@NonNull JavaContext context, @NonNull PsiElement argument) {
        if (argument instanceof PsiConditionalExpression) {
            PsiConditionalExpression expression = (PsiConditionalExpression) argument;
            if (expression.getThenExpression() != null) {
                checkPx(context, expression.getThenExpression());
            }
            if (expression.getElseExpression() != null) {
                checkPx(context, expression.getElseExpression());
            }
            return;
        }

        EnumSet<ResourceType> types = ResourceEvaluator.getResourceTypes(context.getEvaluator(),
          argument);

        if (types != null && types.contains(DIMEN)) {
            String message = String.format(
              "Should pass resolved pixel dimension instead of resource id here: " +
                "`getResources().getDimension*(%1$s)`", argument.getText());
            report(context, COLOR_USAGE, argument, context.getLocation(argument), message);
        }
    }

    private void checkPermission(
            @NonNull JavaContext context,
            @NonNull PsiElement node,
            @Nullable PsiMethod method,
            @Nullable Result result,
            @NonNull PermissionRequirement requirement) {
        if (requirement.isConditional()) {
            return;
        }
        PermissionHolder permissions = getPermissions(context);
        if (!requirement.isSatisfied(permissions)) {
            // See if it looks like we're holding the permission implicitly by @RequirePermission
            // annotations in the surrounding context
            permissions  = addLocalPermissions(permissions, node);
            if (!requirement.isSatisfied(permissions)) {
                Operation operation;
                String name;
                if (result != null) {
                    name = result.name;
                    operation = result.operation;
                } else {
                    assert method != null;
                    PsiClass containingClass = method.getContainingClass();
                    if (containingClass != null) {
                        name = containingClass.getName() + "." + method.getName();
                    } else {
                        name = method.getName();
                    }
                    operation = Operation.CALL;
                }

                String message = String.format(
                        "Missing permissions required %1$s %2$s: %3$s", operation.prefix(),
                        name, requirement.describeMissingPermissions(permissions));
                Location location = context.getLocation(node);
                report(context, MISSING_PERMISSION, node, location, message,
                        // Pass data to IDE quickfix: names to add, and max applicable API version
                        new Object[]{
                                requirement.getMissingPermissions(permissions),
                                requirement.getLastApplicableApi()
                        });
            }
        } else if (requirement.isRevocable(permissions) &&
                context.getMainProject().getTargetSdkVersion().getFeatureLevel() >= 23 &&
                requirement.getLastApplicableApi() >= 23) {

            boolean handlesMissingPermission = handlesSecurityException(node);

            // If not, check to see if the code is deliberately checking to see if the
            // given permission is available.
            if (!handlesMissingPermission) {
                PsiMethod methodNode = PsiTreeUtil.getParentOfType(node, PsiMethod.class, true);
                if (methodNode != null) {
                    CheckPermissionVisitor visitor = new CheckPermissionVisitor(node);
                    methodNode.accept(visitor);
                    handlesMissingPermission = visitor.checksPermission();
                }
            }

            if (!handlesMissingPermission) {
                String message =
                    "Call requires permission which may be rejected by user: code should explicitly "
                    + "check to see if permission is available (with `checkPermission`) or explicitly "
                    + "handle a potential `SecurityException`";
                Location location = context.getLocation(node);
                report(context, MISSING_PERMISSION, node, location, message,
                        // Pass data to IDE quickfix: revocable names, and permission requirement
                        new Object[]{
                                requirement.getRevocablePermissions(permissions),
                                requirement
                        });
            }
        }
    }

    private static boolean handlesSecurityException(@NonNull PsiElement node) {
        // Ensure that the caller is handling a security exception
        // First check to see if we're inside a try/catch which catches a SecurityException
        // (or some wider exception than that). Check for nested try/catches too.
        PsiElement parent = node;
        while (true) {
            PsiTryStatement tryCatch = PsiTreeUtil
                    .getParentOfType(parent, PsiTryStatement.class, true);
            if (tryCatch == null) {
                break;
            } else {
                for (PsiCatchSection psiCatchSection : tryCatch.getCatchSections()) {
                    PsiType type = psiCatchSection.getCatchType();
                    if (isSecurityException(type)) {
                        return true;
                    }
                }

                parent = tryCatch;
            }
        }

        // If not, check to see if the method itself declares that it throws a
        // SecurityException or something wider.
        PsiMethod declaration = PsiTreeUtil.getParentOfType(parent, PsiMethod.class, false);
        if (declaration != null) {
            for (PsiClassType type : declaration.getThrowsList().getReferencedTypes()) {
                if (isSecurityException(type)) {
                    return true;
                }
            }
        }

        return false;
    }

    @NonNull
    private static PermissionHolder addLocalPermissions(
            @NonNull PermissionHolder permissions,
            @NonNull PsiElement node) {
        // Accumulate @RequirePermissions available in the local context
        PsiMethod method = PsiTreeUtil.getParentOfType(node, PsiMethod.class, true);
        if (method == null) {
            return permissions;
        }
        PsiAnnotation annotation = method.getModifierList().findAnnotation(PERMISSION_ANNOTATION);
        permissions = mergeAnnotationPermissions(permissions, annotation);

        PsiClass containingClass = method.getContainingClass();
        if (containingClass != null) {
            PsiModifierList modifierList = containingClass.getModifierList();
            if (modifierList != null) {
                annotation = modifierList.findAnnotation(PERMISSION_ANNOTATION);
                permissions = mergeAnnotationPermissions(permissions, annotation);
            }
        }
        return permissions;
    }

    @NonNull
    private static PermissionHolder mergeAnnotationPermissions(
            @NonNull PermissionHolder permissions,
            @Nullable PsiAnnotation annotation) {
        if (annotation != null) {
            PermissionRequirement requirement = PermissionRequirement.create(annotation);
            permissions = SetPermissionLookup.join(permissions, requirement);
        }

        return permissions;
    }

    /**
     * Visitor which looks through a method, up to a given call (the one requiring a
     * permission) and checks whether it's preceded by a call to checkPermission or
     * checkCallingPermission or enforcePermission etc.
     * <p>
     * Currently it only looks for the presence of this check; it does not perform
     * flow analysis to determine whether the check actually affects program flow
     * up to the permission call, or whether the check permission is checking for
     * permissions sufficient to satisfy the permission requirement of the target call,
     * or whether the check return value (== PERMISSION_GRANTED vs != PERMISSION_GRANTED)
     * is handled correctly, etc.
     */
    private static class CheckPermissionVisitor extends JavaRecursiveElementVisitor {
        private boolean mChecksPermission;
        private boolean mDone;
        private final PsiElement mTarget;

        public CheckPermissionVisitor(@NonNull PsiElement target) {
            mTarget = target;
        }

        @Override
        public void visitElement(PsiElement element) {
            if (!mDone) {
                super.visitElement(element);
            }
        }

        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression node) {
            if (node == mTarget) {
                mDone = true;
            }

            String name = node.getMethodExpression().getReferenceName();
            if (name != null
                    && (name.startsWith("check") || name.startsWith("enforce"))
                    && name.endsWith("Permission")) {
                mChecksPermission = true;
                mDone = true;
            }
        }

        public boolean checksPermission() {
            return mChecksPermission;
        }
    }

    private static boolean isSecurityException(
            @Nullable PsiType type) {
        if (type instanceof PsiClassType) {
            PsiClass cls = ((PsiClassType) type).resolve();
            // In earlier versions we checked not just for java.lang.SecurityException but
            // any super type as well, however that probably hides warnings in cases where
            // users don't want that; see http://b.android.com/182165
            //return context.getEvaluator().extendsClass(cls, "java.lang.SecurityException", false);
            return cls != null && "java.lang.SecurityException".equals(cls.getQualifiedName());
        } else if (type instanceof PsiDisjunctionType) {
            for (PsiType disjunction : ((PsiDisjunctionType)type).getDisjunctions()) {
                if (isSecurityException(disjunction)) {
                    return true;
                }
            }
        }

        return false;
    }

    private PermissionHolder mPermissions;

    private PermissionHolder getPermissions(
            @NonNull JavaContext context) {
        if (mPermissions == null) {
            Set<String> permissions = Sets.newHashSetWithExpectedSize(30);
            Set<String> revocable = Sets.newHashSetWithExpectedSize(4);
            LintClient client = context.getClient();
            // Gather permissions from all projects that contribute to the
            // main project.
            Project mainProject = context.getMainProject();
            for (File manifest : mainProject.getManifestFiles()) {
                addPermissions(client, permissions, revocable, manifest);
            }
            for (Project library : mainProject.getAllLibraries()) {
                for (File manifest : library.getManifestFiles()) {
                    addPermissions(client, permissions, revocable, manifest);
                }
            }

            AndroidVersion minSdkVersion = mainProject.getMinSdkVersion();
            AndroidVersion targetSdkVersion = mainProject.getTargetSdkVersion();
            mPermissions = new SetPermissionLookup(permissions, revocable, minSdkVersion,
                targetSdkVersion);
        }

        return mPermissions;
    }

    private static void addPermissions(@NonNull LintClient client,
            @NonNull Set<String> permissions,
            @NonNull Set<String> revocable,
            @NonNull File manifest) {
        CharSequence xml = client.readFile(manifest);
        Document document = CharSequences.parseDocumentSilently(xml, true);
        if (document == null) {
            return;
        }
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }
        NodeList children = root.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node item = children.item(i);
            if (item.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String nodeName = item.getNodeName();
            if (nodeName.equals(TAG_USES_PERMISSION)
                || nodeName.equals(TAG_USES_PERMISSION_SDK_23)
                || nodeName.equals(TAG_USES_PERMISSION_SDK_M)) {
                Element element = (Element)item;
                String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (!name.isEmpty()) {
                    permissions.add(name);
                }
            } else if (nodeName.equals(TAG_PERMISSION)) {
                Element element = (Element)item;
                String protectionLevel = element.getAttributeNS(ANDROID_URI,
                        ATTR_PROTECTION_LEVEL);
                if (VALUE_DANGEROUS.equals(protectionLevel)) {
                    String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (!name.isEmpty()) {
                        revocable.add(name);
                    }
                }
            }
        }
    }

    private static void checkResult(@NonNull JavaContext context, @NonNull PsiElement node,
            @NonNull PsiMethod method, @NonNull PsiAnnotation annotation) {
        if (skipParentheses(node.getParent()) instanceof PsiExpressionStatement) {
            String methodName = JavaContext.getMethodName(node);
            String suggested = getAnnotationStringValue(annotation, ATTR_SUGGEST);

            // Failing to check permissions is a potential security issue (and had an existing
            // dedicated issue id before which people may already have configured with a
            // custom severity in their LintOptions etc) so continue to use that issue
            // (which also has category Security rather than Correctness) for these:
            Issue issue = CHECK_RESULT;
            if (methodName != null && methodName.startsWith("check")
                    && methodName.contains("Permission")) {
                issue = CHECK_PERMISSION;
            }

            String message = String.format("The result of `%1$s` is not used",
                    methodName);
            if (suggested != null) {
                // TODO: Resolve suggest attribute (e.g. prefix annotation class if it starts
                // with "#" etc?
                message = String.format(
                        "The result of `%1$s` is not used; did you mean to call `%2$s`?",
                        methodName, suggested);
            } else if ("intersect".equals(methodName)
                    && context.getEvaluator().isMemberInClass(method, "android.graphics.Rect")) {
                message += ". If the rectangles do not intersect, no change is made and the "
                        + "original rectangle is not modified. These methods return false to "
                        + "indicate that this has happened.";
            }
            report(context, issue, node, context.getLocation(node), message);
        }
    }

    private static boolean isTestContext(
            @NonNull JavaContext context,
            @NonNull PsiElement element) {
        // (1) Is this compilation unit in a test source path?
        if (context.isTestSource()) {
            return true;
        }

        // (2) Is this AST node surrounded by a test-only annotation?
        while (true) {
            PsiModifierListOwner owner = PsiTreeUtil.getParentOfType(element,
                    PsiModifierListOwner.class, true);
            if (owner == null) {
                break;
            }

            PsiModifierList modifierList = owner.getModifierList();
            if (modifierList != null) {
                for (PsiAnnotation annotation : modifierList.getAnnotations()) {
                    String name = annotation.getQualifiedName();
                    if (RESTRICT_TO_ANNOTATION.equals(name)) {
                        int restrictionScope = getRestrictionScope(annotation);
                        if ((restrictionScope & RESTRICT_TO_TESTS) != 0) {
                            return true;
                        }
                    } else if (VISIBLE_FOR_TESTING_ANNOTATION.equals(name)) {
                        return true;
                    }
                }
            }

            element = owner;
        }

        return false;
    }

    public static void checkVisibleForTesting(
      @NonNull JavaContext context,
      @NonNull PsiElement node,
      @NonNull PsiMethod method,
      @NonNull PsiAnnotation annotation,
      @NonNull PsiAnnotation[] allMethodAnnotations,
      @NonNull PsiAnnotation[] allClassAnnotations) {

        int visibility = getVisibilityForTesting(annotation);
        if (visibility == VISIBILITY_NONE) { // not the default
            checkRestrictTo(context, node, method, annotation, allMethodAnnotations,
                    allClassAnnotations, RESTRICT_TO_TESTS);
        } else {
            // Check that the target method is available
            // (1) private is available in the same compilation unit
            // (2) package private is available in the same package
            // (3) protected is available either from subclasses or in same package
            PsiFile containingFile = node.getContainingFile();
            PsiFile containingFile2 = method.getContainingFile();
            if (Objects.equals(containingFile, containingFile2)) {
                // Same compilation unit
                return;
            }

            // Default constructor? If so the method resolve doesn't point to a
            // method, so look up the class instead to get a valid file to compare
            // with
            if (containingFile2 == null && node instanceof PsiNewExpression) {
                PsiJavaCodeReferenceElement reference = ((PsiNewExpression) node)
                        .getClassReference();
                if (reference != null) {
                    containingFile2 = reference.getContainingFile();
                    if (Objects.equals(containingFile, containingFile2)) {
                        // Same compilation unit
                        return;
                    }
                }
            }

            if (visibility == VISIBILITY_PRIVATE) {
                if (!isTestContext(context, node)) {
                    reportVisibilityError(context, node, "private");
                }
                return;
            }

            JavaEvaluator evaluator = context.getEvaluator();
            PsiPackage pkg = evaluator.getPackage(node);
            PsiPackage methodPackage = evaluator.getPackage(method);
            if (Objects.equals(pkg, methodPackage)) {
                // Same package
                return;
            }
            if (visibility == VISIBILITY_PACKAGE_PRIVATE) {
                if (!isTestContext(context, node)) {
                    reportVisibilityError(context, node, "package private");
                }
                return;
            }

            assert visibility == VISIBILITY_PROTECTED;

            PsiClass methodClass = method.getContainingClass();
            PsiClass thisClass = PsiTreeUtil.getParentOfType(node, PsiClass.class, true);
            if (thisClass == null || methodClass == null) {
                return;
            }
            String qualifiedName = methodClass.getQualifiedName();
            if (qualifiedName == null || evaluator.inheritsFrom(thisClass, qualifiedName, false)) {
                return;
            }

            if (!isTestContext(context, node)) {
                reportVisibilityError(context, node, "protected");
            }
        }
    }

    private static void reportVisibilityError(
            @NonNull JavaContext context,
            @NonNull PsiElement node,
            @NonNull String desc) {
        String message = String.format("This method should only be accessed from tests "
                //+ "(intended visibility is %1$s)", desc);
                + "or within %1$s scope", desc);
        report(context, TEST_VISIBILITY, node, context.getLocation(node), message);
    }

    // Must match constants in @VisibleForTesting:
    private static final int VISIBILITY_PRIVATE           = 2;
    private static final int VISIBILITY_PACKAGE_PRIVATE   = 3;
    private static final int VISIBILITY_PROTECTED         = 4;
    private static final int VISIBILITY_NONE              = 5;

    public static int getVisibilityForTesting(@NonNull PsiAnnotation annotation) {
        PsiAnnotationMemberValue value = annotation.findAttributeValue(ATTR_OTHERWISE);
        if (value instanceof PsiLiteral) {
            Object v = ((PsiLiteral) value).getValue();
            if (v instanceof Integer) {
                return (Integer)v;
            }
        } else if (value instanceof PsiReferenceExpression) {
            // Not compiled; this is unlikely (but can happen when editing the support
            // library project itself)
            PsiReferenceExpression referenceExpression = (PsiReferenceExpression)value;
            String name = referenceExpression.getReferenceName();
            if ("NONE".equals(name)) {
                return VISIBILITY_NONE;
            } else if ("PRIVATE".equals(name)) {
                return VISIBILITY_PRIVATE;
            } else if ("PROTECTED".equals(name)) {
                return VISIBILITY_PROTECTED;
            } else if ("PACKAGE_PRIVATE".equals(name)) {
                return VISIBILITY_PACKAGE_PRIVATE;
            }
        }

        return VISIBILITY_PRIVATE; // the default
    }

    // TODO: Test XML access of restricted classes
    public static void checkRestrictTo(
            @NonNull JavaContext context,
            @NonNull PsiElement node,
            @NonNull PsiMethod method,
            @NonNull PsiAnnotation annotation,
            @NonNull PsiAnnotation[] allMethodAnnotations,
            @NonNull PsiAnnotation[] allClassAnnotations) {
        int scope = getRestrictionScope(annotation);
        if (scope != 0) {
            checkRestrictTo(context, node, method, annotation, allMethodAnnotations,
                    allClassAnnotations, scope);
        }
    }

    private static void checkRestrictTo(
            @NonNull JavaContext context,
            @NonNull PsiElement node,
            @NonNull PsiMethod method,
            @NonNull PsiAnnotation annotation,
            @NonNull PsiAnnotation[] allMethodAnnotations,
            @NonNull PsiAnnotation[] allClassAnnotations,
            int scope) {

        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return;
        }

        if (containsAnnotation(allMethodAnnotations, annotation)) {
            // Make sure that the annotation is *not* inherited.
            // For example, NavigationView (a public, exposed class) extends ScrimInsetsFrameLayout, which
            // is a restricted class. We don't want to make all uses of NavigationView to suddenly be
            // treated as Restricted just because it inherits code from a restricted API.
            if (context.getEvaluator().isInherited(annotation, method)) {
                return;
            }
        } else {
            // Found restriction or class or package: make sure we only check on the most
            // specific scope, otherwise we report the same error multiple times
            // or report errors on restrictions that have been redefined
            if (containsRestrictionAnnotation(allMethodAnnotations)) {
                return;
            }
            boolean isClassAnnotation = containsAnnotation(allClassAnnotations, annotation);
            if (isClassAnnotation) {
                if (context.getEvaluator().isInherited(annotation, containingClass)) {
                    return;
                }
            } else if (containsRestrictionAnnotation(allClassAnnotations)) {
                return;
            }
        }

        if ((scope & RESTRICT_TO_LIBRARY_GROUP) != 0) {
            JavaEvaluator evaluator = context.getEvaluator();
            MavenCoordinates thisCoordinates = evaluator.getLibrary(node);
            MavenCoordinates methodCoordinates = evaluator.getLibrary(method);
            String thisGroup = thisCoordinates != null ? thisCoordinates.getGroupId() : null;
            String methodGroup = methodCoordinates != null ? methodCoordinates.getGroupId() : null;
            if (!Objects.equals(thisGroup, methodGroup) && methodGroup != null) {
                String where = String.format("from within the same library group "
                                + "(groupId=%1$s)", methodGroup);
                reportRestriction(annotation, where, containingClass, method, context,
                        node);
            }
        }

        if ((scope & RESTRICT_TO_LIBRARY) != 0) {
            JavaEvaluator evaluator = context.getEvaluator();
            MavenCoordinates thisCoordinates = evaluator.getLibrary(node);
            MavenCoordinates methodCoordinates = evaluator.getLibrary(method);
            String thisGroup = thisCoordinates != null ? thisCoordinates.getGroupId() : null;
            String methodGroup = methodCoordinates != null ? methodCoordinates.getGroupId() : null;
            if (!Objects.equals(thisGroup, methodGroup) && methodGroup != null) {
                String thisArtifact = thisCoordinates != null ? thisCoordinates.getArtifactId() : null;
                String methodArtifact = methodCoordinates.getArtifactId();
                if (!Objects.equals(thisArtifact, methodArtifact)) {
                    String where = String.format("from within the same library "
                            + "(%1$s:%2$s)", methodGroup, methodArtifact);
                    reportRestriction(annotation, where, containingClass, method, context,
                            node);
                }
            }
        }

        if ((scope & RESTRICT_TO_TESTS )!= 0) {
            if (!isTestContext(context, node)) {
                reportRestriction(annotation, "from tests", containingClass, method, context, node);
            }
        }

        if ((scope & RESTRICT_TO_SUBCLASSES )!= 0) {
            String qualifiedName = containingClass.getQualifiedName();
            if (qualifiedName != null) {
                JavaEvaluator evaluator = context.getEvaluator();

                PsiClass outer;
                boolean isSubClass = false;
                PsiElement prev = node;
                while ((outer = PsiTreeUtil.getParentOfType(prev, PsiClass.class, true)) != null) {
                    if (evaluator.inheritsFrom(outer, qualifiedName, false)) {
                        isSubClass = true;
                        break;
                    }

                    if (evaluator.isStatic(outer)) {
                        break;
                    }
                    prev = outer;
                }

                if (!isSubClass) {
                    reportRestriction(annotation, "from subclasses", containingClass, method,
                            context, node);
                }
            }
        }
    }

    private static void reportRestriction(
            @NonNull PsiAnnotation annotation,
            @NonNull String where,
            @NonNull PsiClass containingClass,
            @NonNull PsiMethod method,
            @NonNull JavaContext context,
            @NonNull PsiElement node) {
        String api;
        if (method.isConstructor()) {
            api = method.getName() + " constructor";
        } else {
            api = containingClass.getName() + "." + method.getName();
        }

        PsiElement locationNode = node;
        if (node instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression callExpression = (PsiMethodCallExpression) node;
            PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
            PsiElement nameElement = methodExpression.getReferenceNameElement();
            if (nameElement != null) {
                locationNode = nameElement;
            }

            // If the annotation was reported on the class, and the left hand side
            // expression is that class, use it as the name node?
            if (annotation.getOwner() instanceof PsiModifierList) {
                PsiModifierList modifierList = (PsiModifierList)annotation.getOwner();
                PsiClass cls = null;
                if (modifierList instanceof PsiClass) {
                    // In the ECJ bridge we sometimes collapse modifier lists into
                    // the same PSI instance as the class itself
                    cls = (PsiClass) modifierList;
                } else if (!(modifierList instanceof PsiMethod)) { // methods are collapsed too
                    PsiElement parent = modifierList.getParent();
                    if (parent instanceof PsiClass) {
                        cls = (PsiClass) parent;
                    }
                }
                if (cls != null) {
                    PsiExpression qualifier = methodExpression.getQualifierExpression();
                    String className = cls.getName();
                    if (qualifier != null && className != null && qualifier.textMatches(className)) {
                        locationNode = qualifier;
                        api = className;
                    }
                }
            }
        }

        // If this error message changes, you need to also update ResourceTypeInspection#guessLintIssue
        String message = api + " can only be called " + where;


        // Most users will encounter this for the support library; let's have a clearer error message
        // for that specific scenario
        if (where.equals("from within the same library (groupId=com.android.support)")) {
            // If this error message changes, you need to also update ResourceTypeInspection#guessLintIssue
            message = "This API is marked as internal to the support library and should not be accessed from apps";
        }

        Location location = context.getLocation(locationNode);
        report(context, RESTRICTED, node, location, message, null);
    }

    /** {@code RestrictTo(RestrictTo.Scope.GROUP_ID} */
    @SuppressWarnings("PointlessBitwiseExpression")
    private static final int RESTRICT_TO_LIBRARY_GROUP = 1 << 0;
    /** {@code RestrictTo(RestrictTo.Scope.GROUP_ID} */
    @SuppressWarnings("PointlessBitwiseExpression")
    private static final int RESTRICT_TO_LIBRARY       = 1 << 1;
    /** {@code RestrictTo(RestrictTo.Scope.TESTS} */
    private static final int RESTRICT_TO_TESTS         = 1 << 2;
    /** {@code RestrictTo(RestrictTo.Scope.SUBCLASSES} */
    private static final int RESTRICT_TO_SUBCLASSES    = 1 << 3;

    public static int getRestrictionScope(@NonNull PsiAnnotation annotation) {
        PsiAnnotationMemberValue value = annotation.findAttributeValue(ATTR_VALUE);
        return getRestrictionScope(value);
    }

    private static int getRestrictionScope(@Nullable PsiAnnotationMemberValue value) {
        int scope = 0;
        if (value != null) {
            if (value instanceof PsiArrayInitializerMemberValue) {
                PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue) value)
                        .getInitializers();
                for (PsiAnnotationMemberValue initializer : initializers) {
                    scope |= getRestrictionScope(initializer);
                }
            } else if (value instanceof PsiReferenceExpression) {
                PsiElement resolved = ((PsiReferenceExpression) value).resolve();
                if (resolved instanceof PsiField) {
                    String name = ((PsiField) resolved).getName();
                    if ("GROUP_ID".equals(name) || "LIBRARY_GROUP".equals(name)) {
                        scope |= RESTRICT_TO_LIBRARY_GROUP;
                    } else if ("SUBCLASSES".equals(name)) {
                        scope |= RESTRICT_TO_SUBCLASSES;
                    } else if ("TESTS".equals(name)) {
                        scope |= RESTRICT_TO_TESTS;
                    } else if ("LIBRARY".equals(name)) {
                        scope |= RESTRICT_TO_LIBRARY;
                    }
                }
            }
        }

        return scope;
    }

    private static void checkThreading(
            @NonNull JavaContext context,
            @NonNull PsiElement node,
            @NonNull PsiMethod method,
            @NonNull String signature,
            @NonNull PsiAnnotation annotation,
            @NonNull PsiAnnotation[] allMethodAnnotations,
            @NonNull PsiAnnotation[] allClassAnnotations) {
        List<String> threadContext = getThreadContext(context, node);
        if (threadContext != null && !isCompatibleThread(threadContext, signature)) {
            // If the annotation is specified on the class, ignore this requirement
            // if there is another annotation specified on the method.
            if (containsAnnotation(allClassAnnotations, annotation)) {
                if (containsThreadingAnnotation(allMethodAnnotations)) {
                    return;
                }
                // Make sure ALL the other context annotations are acceptable!
            } else {
                assert containsAnnotation(allMethodAnnotations, annotation);
                // See if any of the *other* annotations are compatible.
                Boolean isFirst = null;
                for (PsiAnnotation other : allMethodAnnotations) {
                    if (other == annotation) {
                        if (isFirst == null) {
                            isFirst = true;
                        }
                        continue;
                    } else if (!isThreadingAnnotation(other)) {
                        continue;
                    }
                    if (isFirst == null) {
                        // We'll be called for each annotation on the method.
                        // For each one we're checking *all* annotations on the target.
                        // Therefore, when we're seeing the second, third, etc annotation
                        // on the method we've already checked them, so return here.
                        return;
                    }
                    String s = other.getQualifiedName();
                    if (s != null && isCompatibleThread(threadContext, s)) {
                        return;
                    }
                }
            }

            String name = method.getName();
            if ((name.startsWith("post") )
                && context.getEvaluator().isMemberInClass(method, CLASS_VIEW)) {
                // The post()/postDelayed() methods are (currently) missing
                // metadata (@AnyThread); they're on a class marked @UiThread
                // but these specific methods are not @UiThread.
                return;
            }

            List<String> targetThreads = getThreads(context, method);
            if (targetThreads == null) {
                targetThreads = Collections.singletonList(signature);
            }

            String message = String.format(
                 "%1$s %2$s must be called from the %3$s thread, currently inferred thread is %4$s thread",
                 method.isConstructor() ? "Constructor" : "Method",
                 method.getName(), describeThreads(targetThreads, true),
                 describeThreads(threadContext, false));
            report(context, THREAD, node, context.getLocation(node), message);
        }
    }

    public static boolean containsAnnotation(
            @NonNull PsiAnnotation[] array,
            @NonNull PsiAnnotation annotation) {
        for (PsiAnnotation a : array) {
            if (a == annotation) {
                return true;
            }
        }

        return false;
    }

    public static boolean containsRestrictionAnnotation(@NonNull PsiAnnotation[] array) {
        return containsAnnotation(array, RESTRICT_TO_ANNOTATION);
    }

    public static boolean containsAnnotation(
            @NonNull PsiAnnotation[] array,
            @NonNull String qualifiedName) {
        for (PsiAnnotation annotation : array) {
            if (qualifiedName.equals(annotation.getQualifiedName())) {
                return true;
            }
        }

        return false;
    }

    public static boolean containsThreadingAnnotation(@NonNull PsiAnnotation[] array) {
        for (PsiAnnotation annotation : array) {
            if (isThreadingAnnotation(annotation)) {
                return true;
            }
        }

        return false;
    }

    public static boolean isThreadingAnnotation(@NonNull PsiAnnotation annotation) {
        String signature = annotation.getQualifiedName();
        return signature != null
                && signature.endsWith(THREAD_SUFFIX)
                && signature.startsWith(SUPPORT_ANNOTATIONS_PREFIX);
    }

    @NonNull
    public static String describeThreads(@NonNull List<String> annotations, boolean any) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < annotations.size(); i++) {
            if (i > 0) {
                if (i == annotations.size() - 1) {
                    if (any) {
                        sb.append(" or ");
                    } else {
                        sb.append(" and ");
                    }
                } else {
                    sb.append(", ");
                }
            }
            sb.append(describeThread(annotations.get(i)));
        }
        return sb.toString();
    }

    @NonNull
    public static String describeThread(@NonNull String annotation) {
        switch (annotation) {
            case UI_THREAD_ANNOTATION:
                return "UI";
            case MAIN_THREAD_ANNOTATION:
                return "main";
            case BINDER_THREAD_ANNOTATION:
                return "binder";
            case WORKER_THREAD_ANNOTATION:
                return "worker";
            case ANY_THREAD_ANNOTATION:
                return "any";
            default:
                return "other";
        }
    }

    /** returns true if the two threads are compatible */
    public static boolean isCompatibleThread(@NonNull List<String> callers,
            @NonNull String callee) {
        // ALL calling contexts must be valid
        assert !callers.isEmpty();
        for (String caller : callers) {
            if (!isCompatibleThread(caller, callee)) {
                return false;
            }
        }

        return true;
    }

    /** returns true if the two threads are compatible */
    public static boolean isCompatibleThread(@NonNull String caller, @NonNull String callee) {
        if (callee.equals(caller)) {
            return true;
        }

        if (callee.equals(ANY_THREAD_ANNOTATION)) {
            return true;
        }

        // Allow @UiThread and @MainThread to be combined
        if (callee.equals(UI_THREAD_ANNOTATION)) {
            if (caller.equals(MAIN_THREAD_ANNOTATION)) {
                return true;
            }
        } else if (callee.equals(MAIN_THREAD_ANNOTATION)) {
            if (caller.equals(UI_THREAD_ANNOTATION)) {
                return true;
            }
        }

        return false;
    }

    /** Attempts to infer the current thread context at the site of the given method call */
    @Nullable
    public static List<String> getThreadContext(@NonNull JavaContext context,
            @NonNull PsiElement methodCall) {
        //noinspection unchecked
        PsiMethod method = PsiTreeUtil.getParentOfType(methodCall, PsiMethod.class, true,
                PsiAnonymousClass.class, PsiLambdaExpression.class);
        return getThreads(context, method);
    }

    /** Attempts to infer the current thread context at the site of the given method call */
    @Nullable
    private static List<String> getThreads(@NonNull JavaContext context,
            @Nullable PsiMethod method) {
        if (method != null) {
            List<String> result = null;
            PsiClass cls = method.getContainingClass();

            while (method != null) {
                for (PsiAnnotation annotation : method.getModifierList().getAnnotations()) {
                    String name = annotation.getQualifiedName();
                    if (name != null && name.startsWith(SUPPORT_ANNOTATIONS_PREFIX)
                            && name.endsWith(THREAD_SUFFIX)) {
                        if (result == null) {
                            result = new ArrayList<>(4);
                        }
                        result.add(name);
                    }
                }
                if (result != null) {
                    // We don't accumulate up the chain: one method replaces the requirements
                    // of its super methods.
                    return result;
                }
                method = context.getEvaluator().getSuperMethod(method);
            }

            // See if we're extending a class with a known threading context
            while (cls != null) {
                PsiModifierList modifierList = cls.getModifierList();
                if (modifierList != null) {
                    for (PsiAnnotation annotation : modifierList.getAnnotations()) {
                        String name = annotation.getQualifiedName();
                        if (name != null && name.startsWith(SUPPORT_ANNOTATIONS_PREFIX)
                                && name.endsWith(THREAD_SUFFIX)) {
                            if (result == null) {
                                result = new ArrayList<>(4);
                            }
                            result.add(name);
                        }
                    }
                    if (result != null) {
                        // We don't accumulate up the chain: one class replaces the requirements
                        // of its super classes.
                        return result;
                    }
                }
                cls = cls.getSuperClass();
            }
        }

        // In the future, we could also try to infer the threading context using
        // other heuristics. For example, if we're in a method with unknown threading
        // context, but we see that the method is called by another method with a known
        // threading context, we can infer that that threading context is the context for
        // this thread too (assuming the call is direct).

        return null;
    }

    private static boolean isNumber(@NonNull PsiElement argument) {
        if (argument instanceof PsiLiteral) {
            Object value = ((PsiLiteral) argument).getValue();
            return value instanceof Number;
        } else if (argument instanceof PsiPrefixExpression) {
            PsiPrefixExpression expression = (PsiPrefixExpression) argument;
            PsiExpression operand = expression.getOperand();
            return operand != null && isNumber(operand);
        } else {
            return false;
        }
    }

    private static boolean isZero(@NonNull PsiElement argument) {
        if (argument instanceof PsiLiteral) {
            Object value = ((PsiLiteral) argument).getValue();
            return value instanceof Number && ((Number)value).intValue() == 0;
        }
        return false;
    }

    private static boolean isMinusOne(@NonNull PsiElement argument) {
        if (argument instanceof PsiPrefixExpression) {
            PsiPrefixExpression expression = (PsiPrefixExpression) argument;
            PsiExpression operand = expression.getOperand();
            if (operand instanceof PsiLiteral &&
                    expression.getOperationTokenType() == JavaTokenType.MINUS) {
                Object value = ((PsiLiteral) operand).getValue();
                return value instanceof Number && ((Number) value).intValue() == 1;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private static void checkResourceType(
            @NonNull JavaContext context,
            @NonNull PsiElement argument,
            @NonNull EnumSet<ResourceType> expectedType,
            @NonNull PsiElement call,
            @Nullable PsiMethod calledMethod) {
        EnumSet<ResourceType> actual = ResourceEvaluator.getResourceTypes(context.getEvaluator(),
                argument);

        //noinspection IfStatementWithIdenticalBranches
        if (actual == null && (!isNumber(argument) || isZero(argument) || isMinusOne(argument)) ) {
            return;
        } else if (actual != null && (!Sets.intersection(actual, expectedType).isEmpty()
                || expectedType.contains(DRAWABLE)
                && (actual.contains(COLOR) || actual.contains(MIPMAP)))) {
            return;
        }

        if (expectedType.contains(ResourceType.STYLEABLE) && (expectedType.size() == 1)
                && calledMethod != null
                && context.getEvaluator().isMemberInClass(calledMethod,
                        "android.content.res.TypedArray")
                && (call instanceof PsiMethodCallExpression)
                && typeArrayFromArrayLiteral(((PsiMethodCallExpression) call)
                    .getMethodExpression().getQualifierExpression())) {
            // You're generally supposed to provide a styleable to the TypedArray methods,
            // but you're also allowed to supply an integer array
            return;
        }

        String message;
        if (actual != null && actual.size() == 1 && actual.contains(
                ResourceEvaluator.COLOR_INT_MARKER_TYPE)) {
            message = "Expected a color resource id (`R.color.`) but received an RGB integer";
        } else if (expectedType.contains(ResourceEvaluator.COLOR_INT_MARKER_TYPE)) {
            message = String.format("Should pass resolved color instead of resource id here: " +
              "`getResources().getColor(%1$s)`", argument.getText());
        } else if (actual != null && actual.size() == 1 && actual.contains(
          ResourceEvaluator.DIMENSION_MARKER_TYPE)) {
            message = "Expected a dimension resource id (`R.color.`) but received a pixel integer";
        } else if (expectedType.contains(ResourceEvaluator.DIMENSION_MARKER_TYPE)) {
            message = String.format("Should pass resolved pixel size instead of resource id here: " +
              "`getResources().getDimension*(%1$s)`", argument.getText());
        } else if (expectedType.size() < ResourceType.getNames().length - 2) { // -2: marker types
            message = String.format("Expected resource of type %1$s",
                    Joiner.on(" or ").join(expectedType));
        } else {
            message = "Expected resource identifier (`R`.type.`name`)";
        }
        report(context, RESOURCE_TYPE, argument, context.getLocation(argument), message);
    }

    /**
     * Returns true if the node is pointing to a TypedArray whose value was obtained
     * from an array literal
     */
    public static boolean typeArrayFromArrayLiteral(@Nullable PsiElement node) {
        if (node instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression expression = (PsiMethodCallExpression) node;
            String name = expression.getMethodExpression().getReferenceName();
            if (name != null && "obtainStyledAttributes".equals(name)) {
                PsiExpressionList argumentList = expression.getArgumentList();
                PsiExpression[] expressions = argumentList.getExpressions();
                if (expressions.length > 0) {
                    int arg;
                    if (expressions.length == 1) {
                        // obtainStyledAttributes(int[] attrs)
                        arg = 0;
                    } else if (expressions.length == 2) {
                        // obtainStyledAttributes(AttributeSet set, int[] attrs)
                        // obtainStyledAttributes(int resid, int[] attrs)
                        for (arg = 0; arg < expressions.length; arg++) {
                            PsiType type = expressions[arg].getType();
                            if (type instanceof PsiArrayType) {
                                break;
                            }
                        }
                        if (arg == expressions.length) {
                            return false;
                        }
                    } else if (expressions.length == 4) {
                        // obtainStyledAttributes(AttributeSet set, int[] attrs, int defStyleAttr, int defStyleRes)
                        arg = 1;
                    } else {
                        return false;
                    }

                    return ConstantEvaluator.isArrayLiteral(expressions[arg]);
                }
            }
            return false;
        } else if (node instanceof PsiReference) {
            PsiElement resolved = ((PsiReference) node).resolve();
            if (resolved instanceof PsiField) {
                PsiField field = (PsiField) resolved;
                if (field.getInitializer() != null) {
                    return typeArrayFromArrayLiteral(field.getInitializer());
                }
            } else if (resolved instanceof PsiLocalVariable) {
                PsiLocalVariable variable = (PsiLocalVariable) resolved;
                PsiExpression last = ConstantEvaluator.findLastAssignment(node, variable);
                if (last != null) {
                    return typeArrayFromArrayLiteral(last);
                }
            }
        } else if (node instanceof PsiNewExpression) {
            PsiNewExpression creation = (PsiNewExpression) node;
            if (creation.getArrayInitializer() != null) {
                return true;
            }
            PsiType type = creation.getType();
            if (type instanceof PsiArrayType) {
                return true;
            }
        } else if (node instanceof PsiParenthesizedExpression) {
            PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression) node;
            PsiExpression expression = parenthesizedExpression.getExpression();
            if (expression != null) {
                return typeArrayFromArrayLiteral(expression);
            }
        } else if (node instanceof PsiTypeCastExpression) {
            PsiTypeCastExpression castExpression = (PsiTypeCastExpression) node;
            PsiExpression operand = castExpression.getOperand();
            if (operand != null) {
                return typeArrayFromArrayLiteral(operand);
            }
        }

        return false;
    }

    private static void checkIntRange(
            @NonNull JavaContext context,
            @NonNull PsiAnnotation annotation,
            @NonNull PsiElement argument,
            @NonNull PsiAnnotation[] allAnnotations) {
        if (argument instanceof PsiConditionalExpression) {
            PsiConditionalExpression expression = (PsiConditionalExpression) argument;
            if (expression.getThenExpression() != null) {
                checkIntRange(context, annotation, expression.getThenExpression(), allAnnotations);
            }
            if (expression.getElseExpression() != null) {
                checkIntRange(context, annotation, expression.getElseExpression(), allAnnotations);
            }
            return;
        }

        String message = getIntRangeError(context, annotation, argument);
        if (message != null) {
            if (findIntDef(allAnnotations) != null) {
                // Don't flag int range errors if there is an int def annotation there too;
                // there could be a valid @IntDef constant. (The @IntDef check will
                // perform range validation by calling getIntRange.)
                return;
            }

            report(context, RANGE, argument, context.getLocation(argument), message);
        }
    }

    @Nullable
    private static String getIntRangeError(
            @NonNull JavaContext context,
            @NonNull PsiAnnotation annotation,
            @NonNull PsiElement argument) {
        if (argument instanceof PsiNewExpression) {
            PsiNewExpression newExpression = (PsiNewExpression) argument;
            PsiArrayInitializerExpression initializer = newExpression.getArrayInitializer();
            if (initializer != null) {
                for (PsiExpression expression : initializer.getInitializers()) {
                    String error = getIntRangeError(context, annotation, expression);
                    if (error != null) {
                        return error;
                    }
                }
            }
        }

        IntRangeConstraint constraint = IntRangeConstraint.create(annotation);

        Object object = ConstantEvaluator.evaluate(context, argument);
        if (!(object instanceof Number)) {
            // Number arrays
            if (object instanceof int[]
                    || object instanceof long[]) {
                if (object instanceof int[]) {
                    for (int value : (int[]) object) {
                        if (!constraint.isValid(value)) {
                            return constraint.describe(value);
                        }
                    }
                }
                if (object instanceof long[]) {
                    for (long value : (long[]) object) {
                        if (!constraint.isValid(value)) {
                            return constraint.describe(value);
                        }
                    }
                }
            }

            // Try to resolve it; see if there's an annotation on the variable/parameter/field
            if (argument instanceof PsiReference) {
                PsiElement resolved = ((PsiReference) argument).resolve();
                if (resolved instanceof PsiModifierListOwner) {
                    RangeConstraint referenceConstraint =
                            RangeConstraint.create((PsiModifierListOwner) resolved);
                    RangeConstraint here = RangeConstraint.create(annotation);
                    if (here != null && referenceConstraint != null) {
                        Boolean contains = here.contains(referenceConstraint);
                        if (contains != null && !contains) {
                            return here.describe(null);
                        }
                    }
                }
            }

            return null;
        }

        long value = ((Number)object).longValue();
        if (!constraint.isValid(value)) {
            return constraint.describe(value);
        }

        return null;
    }

    private static void checkFloatRange(
            @NonNull JavaContext context,
            @NonNull PsiAnnotation annotation,
            @NonNull PsiElement argument) {
        if (argument instanceof PsiConditionalExpression) {
            PsiConditionalExpression expression = (PsiConditionalExpression) argument;
            if (expression.getThenExpression() != null) {
                checkFloatRange(context, annotation, expression.getThenExpression());
            }
            if (expression.getElseExpression() != null) {
                checkFloatRange(context, annotation, expression.getElseExpression());
            }
            return;
        }

        FloatRangeConstraint constraint = FloatRangeConstraint.create(annotation);
        Object object = ConstantEvaluator.evaluate(context, argument);

        if (!(object instanceof Number)) {
            // Number arrays
            if (object instanceof float[]
                    || object instanceof double[]
                    || object instanceof int[]
                    || object instanceof long[]) {
                if (object instanceof float[]) {
                    for (float value : (float[]) object) {
                        if (!constraint.isValid(value)) {
                            String message = constraint.describe(value);
                            report(context, RANGE, argument, context.getLocation(argument),
                                    message);
                            return;
                        }
                    }
                }
                // Kinda repetitive but primitive arrays are not related by subtyping
                if (object instanceof double[]) {
                    for (double value : (double[]) object) {
                        if (!constraint.isValid(value)) {
                            String message = constraint.describe(value);
                            report(context, RANGE, argument, context.getLocation(argument),
                                    message);
                            return;
                        }
                    }
                }
                if (object instanceof int[]) {
                    for (int value : (int[]) object) {
                        if (!constraint.isValid(value)) {
                            String message = constraint.describe(value);
                            report(context, RANGE, argument, context.getLocation(argument),
                                    message);
                            return;
                        }
                    }
                }
                if (object instanceof long[]) {
                    for (long value : (long[]) object) {
                        if (!constraint.isValid(value)) {
                            String message = constraint.describe(value);
                            report(context, RANGE, argument, context.getLocation(argument),
                                    message);
                            return;
                        }
                    }
                }
            }

            // Try to resolve it; see if there's an annotation on the variable/parameter/field
            if (argument instanceof PsiReference) {
                PsiElement resolved = ((PsiReference) argument).resolve();
                if (resolved instanceof PsiModifierListOwner) {
                    RangeConstraint referenceConstraint =
                            RangeConstraint.create((PsiModifierListOwner) resolved);
                    RangeConstraint here = RangeConstraint.create(annotation);
                    if (here != null && referenceConstraint != null) {
                        Boolean contains = here.contains(referenceConstraint);
                        if (contains != null && !contains) {
                            String message = here.describe(null);
                            report(context, RANGE, argument, context.getLocation(argument), message);
                        }
                    }
                }
            }
            return;
        }

        double value = ((Number)object).doubleValue();
        if (!constraint.isValid(value)) {
            String message = constraint.describe(
                    argument instanceof PsiExpression ? (PsiExpression)argument : null, value);
            report(context, RANGE, argument, context.getLocation(argument), message);
        }
    }

    private static void checkSize(
            @NonNull JavaContext context,
            @NonNull PsiAnnotation annotation,
            @NonNull PsiElement argument) {
        long actual;
        boolean isString = false;

        // TODO: Collections syntax, e.g. Arrays.asList ⇒ param count, emptyList=0, singleton=1, etc
        // TODO: Flow analysis

        if (argument instanceof PsiNewExpression) {
            PsiNewExpression newExpression = (PsiNewExpression) argument;
            PsiArrayInitializerExpression initializer = newExpression.getArrayInitializer();
            if (initializer != null) {
                PsiExpression[] initializers = initializer.getInitializers();
                actual = initializers.length;
            } else {
                return;
            }
        } else if (argument instanceof PsiConditionalExpression) {
            PsiConditionalExpression expression = (PsiConditionalExpression) argument;
            if (expression.getThenExpression() != null) {
                checkSize(context, annotation, expression.getThenExpression());
            }
            if (expression.getElseExpression() != null) {
                checkSize(context, annotation, expression.getElseExpression());
            }
            return;
        } else {
            Object object = ConstantEvaluator.evaluate(context, argument);
            // Check string length
            if (object instanceof String) {
                actual = ((String) object).length();
                isString = true;
            } else {
                actual = getArrayLength(object);
                if (actual == -1) {
                    // Try to resolve it; see if there's an annotation on the variable/parameter/field
                    if (argument instanceof PsiReference) {
                        PsiElement resolved = ((PsiReference) argument).resolve();
                        if (resolved instanceof PsiModifierListOwner) {
                            RangeConstraint constraint = RangeConstraint
                                    .create((PsiModifierListOwner) resolved);
                            RangeConstraint here = RangeConstraint.create(annotation);
                            if (here != null && constraint != null) {
                                Boolean contains = here.contains(constraint);
                                if (contains != null && !contains) {
                                    String message = here.describe(null);
                                    report(context, RANGE, argument, context.getLocation(argument),
                                            message);
                                }
                            }
                        }
                    }

                    return;
                }
            }
        }

        SizeConstraint constraint = SizeConstraint.create(annotation);
        if (!constraint.isValid(actual)) {
            String unit;
            if (isString) {
                unit = "length";
            } else {
                unit = "size";
            }

            String message = constraint.describe(
                    argument instanceof PsiExpression ? (PsiExpression)argument : null,
                    unit, actual);
            report(context, RANGE, argument, context.getLocation(argument), message);
        }
    }

    private static int getArrayLength(@Nullable Object object) {
        // This is kinda repetitive but there is no subtyping relationship between
        // primitive arrays; int[] is not a subtype of Object[] etc.

        if (object instanceof Object[]) {
            return ((Object[]) object).length;
        } else if (object instanceof int[]) {
            return ((int[]) object).length;
        } else if (object instanceof long[]) {
            return ((long[]) object).length;
        } else if (object instanceof float[]) {
            return ((float[]) object).length;
        } else if (object instanceof double[]) {
            return ((double[]) object).length;
        } else if (object instanceof char[]) {
            return ((char[]) object).length;
        } else if (object instanceof byte[]) {
            return ((byte[]) object).length;
        } else if (object instanceof short[]) {
            return ((short[]) object).length;
        }

        return -1;
    }

    @Nullable
    private static PsiAnnotation findIntRange(
            @NonNull PsiAnnotation[] annotations) {
        for (PsiAnnotation annotation : annotations) {
            if (INT_RANGE_ANNOTATION.equals(annotation.getQualifiedName())) {
                return annotation;
            }
        }

        return null;
    }

    @Nullable
    static PsiAnnotation findIntDef(@NonNull PsiAnnotation[] annotations) {
        for (PsiAnnotation annotation : annotations) {
            if (INT_DEF_ANNOTATION.equals(annotation.getQualifiedName())) {
                return annotation;
            }
        }

        return null;
    }

    private static void checkTypeDefConstant(
            @NonNull JavaContext context,
            @NonNull PsiAnnotation annotation,
            @Nullable PsiElement argument,
            @Nullable PsiElement errorNode,
            boolean flag,
            @NonNull PsiAnnotation[] allAnnotations) {
        if (argument == null) {
            return;
        }
        if (argument instanceof PsiLiteral) {
            Object value = ((PsiLiteral) argument).getValue();
            if (value == null) {
                // Accepted for @StringDef
                //noinspection UnnecessaryReturnStatement
                return;
            } else if (value instanceof String) {
                String string = (String) value;
                checkTypeDefConstant(context, annotation, argument, errorNode, false, string,
                        allAnnotations);
            } else if (value instanceof Integer || value instanceof Long) {
                long v = value instanceof Long ? ((Long) value) : ((Integer) value).longValue();
                if (flag && v == 0) {
                    // Accepted for a flag @IntDef
                    return;
                }

                checkTypeDefConstant(context, annotation, argument, errorNode, flag, value,
                        allAnnotations);
            }
        } else if (isMinusOne(argument)) {
            // -1 is accepted unconditionally for flags
            if (!flag) {
                reportTypeDef(context, annotation, argument, errorNode, allAnnotations);
            }
        } else if (argument instanceof PsiPrefixExpression) {
            PsiPrefixExpression expression = (PsiPrefixExpression) argument;
            if (flag) {
                checkTypeDefConstant(context, annotation, expression.getOperand(),
                        errorNode, true, allAnnotations);
            } else {
                IElementType operator = expression.getOperationTokenType();
                if (operator == JavaTokenType.TILDE) {
                    report(context, TYPE_DEF, expression, context.getLocation(expression),
                            "Flag not allowed here");
                } else if (operator == JavaTokenType.MINUS) {
                    reportTypeDef(context, annotation, argument, errorNode, allAnnotations);
                }
            }
        } else if (argument instanceof PsiParenthesizedExpression) {
            PsiExpression expression = ((PsiParenthesizedExpression) argument).getExpression();
            if (expression != null) {
                checkTypeDefConstant(context, annotation, expression, errorNode, flag, allAnnotations);
            }
        } else if (argument instanceof PsiConditionalExpression) {
            PsiConditionalExpression expression = (PsiConditionalExpression) argument;
            if (expression.getThenExpression() != null) {
                checkTypeDefConstant(context, annotation, expression.getThenExpression(), errorNode, flag,
                        allAnnotations);
            }
            if (expression.getElseExpression() != null) {
                checkTypeDefConstant(context, annotation, expression.getElseExpression(), errorNode, flag,
                        allAnnotations);
            }
        } else if (argument instanceof PsiPolyadicExpression) {
            // If it's ?: then check both the if and else clauses
            PsiPolyadicExpression expression = (PsiPolyadicExpression) argument;
            if (flag) {
                for (PsiExpression operand : expression.getOperands()) {
                    checkTypeDefConstant(context, annotation, operand, errorNode,true,
                            allAnnotations);
                }
            } else {
                IElementType operator = expression.getOperationTokenType();
                if (operator == JavaTokenType.AND
                        || operator == JavaTokenType.OR
                        || operator == JavaTokenType.XOR) {
                    report(context, TYPE_DEF, expression, context.getLocation(expression),
                            "Flag not allowed here");
                }
            }
        } else if (argument instanceof PsiReference) {
            PsiElement resolved = ((PsiReference) argument).resolve();
            if (resolved instanceof PsiField) {
                PsiField field = (PsiField) resolved;
                if (field.getType() instanceof PsiArrayType) {
                    // Allow checking the initializer here even if the field itself
                    // isn't final or static; check that the individual values are okay
                    checkTypeDefConstant(context, annotation, argument,
                            errorNode != null ? errorNode : argument,
                            flag, resolved, allAnnotations);
                    return;
                }

                // If it's a constant (static/final) check that it's one of the allowed ones
                if (context.getEvaluator().isStatic(field) &&
                        context.getEvaluator().isFinal(field)) {
                    checkTypeDefConstant(context, annotation, argument,
                            errorNode != null ? errorNode : argument,
                            flag, resolved, allAnnotations);
                }
            } else if (resolved instanceof PsiLocalVariable) {
                PsiLocalVariable variable = (PsiLocalVariable) resolved;
                PsiExpression last = ConstantEvaluator.findLastAssignment(argument, variable);
                if (last != null) {
                    checkTypeDefConstant(context, annotation,
                            last,
                            errorNode != null ? errorNode : argument, flag,
                            allAnnotations);
                }
            }
        } else if (argument instanceof PsiNewExpression) {
            PsiNewExpression newExpression = (PsiNewExpression) argument;
            PsiArrayInitializerExpression initializer = newExpression.getArrayInitializer();
            if (initializer != null) {
                PsiType type = initializer.getType();
                if (type != null) {
                    type = type.getDeepComponentType();
                }
                if (PsiType.INT.equals(type) || PsiType.LONG.equals(type)) {
                    for (PsiExpression expression : initializer.getInitializers()) {
                        checkTypeDefConstant(context, annotation, expression, errorNode, flag,
                                allAnnotations);
                    }
                }
            }
        } else if (argument instanceof PsiArrayInitializerExpression) {
            for (PsiExpression initializer : ((PsiArrayInitializerExpression) argument)
                    .getInitializers()) {
                checkTypeDefConstant(context, annotation, initializer, errorNode, flag,
                        allAnnotations);
            }
        }
    }

    private static void checkTypeDefConstant(@NonNull JavaContext context,
            @NonNull PsiAnnotation annotation, @NonNull PsiElement argument,
            @Nullable PsiElement errorNode, boolean flag, Object value,
            @NonNull PsiAnnotation[] allAnnotations) {
        PsiAnnotation rangeAnnotation = findIntRange(allAnnotations);
        if (rangeAnnotation != null && !(value instanceof PsiField)) {
            // Allow @IntRange on this number, but only if it's a literal, not if it's some
            // other (unrelated) constant)
            if (getIntRangeError(context, rangeAnnotation, argument) == null) {
                return;
            }
        }

        PsiAnnotationMemberValue allowed = getAnnotationValue(annotation);
        if (allowed == null) {
            return;
        }

        if (allowed instanceof PsiArrayInitializerMemberValue) {
            PsiArrayInitializerMemberValue initializerExpression =
                    (PsiArrayInitializerMemberValue) allowed;
            PsiAnnotationMemberValue[] initializers = initializerExpression.getInitializers();
            for (PsiAnnotationMemberValue expression : initializers) {
                if (expression instanceof PsiLiteral) {
                    if (value.equals(((PsiLiteral)expression).getValue())) {
                        return;
                    }
                } else if (expression instanceof PsiReference) {
                    PsiElement resolved = ((PsiReference) expression).resolve();
                    if (resolved != null && resolved.equals(value)) {
                        return;
                    }
                }
            }

            if (value instanceof PsiField && rangeAnnotation == null) {
                PsiField astNode = (PsiField)value;
                PsiExpression initializer = astNode.getInitializer();
                if (initializer != null) {
                    checkTypeDefConstant(context, annotation, initializer, errorNode,
                            flag, allAnnotations);
                    return;
                }
            }

            if (allowed instanceof PsiCompiledElement) {
                // If we for some reason have a compiled annotation, don't flag the error
                // since we can't represent intdef data on these annotations
                return;
            }

            reportTypeDef(context, argument, errorNode, flag,
                    initializers, allAnnotations);
        }
    }

    private static void reportTypeDef(@NonNull JavaContext context,
            @NonNull PsiAnnotation annotation, @NonNull PsiElement argument,
            @Nullable PsiElement errorNode, @NonNull PsiAnnotation[] allAnnotations) {
        PsiAnnotationMemberValue allowed = getAnnotationValue(annotation);
        if (allowed instanceof PsiArrayInitializerMemberValue) {
            PsiArrayInitializerMemberValue initializerExpression =
                    (PsiArrayInitializerMemberValue) allowed;
            PsiAnnotationMemberValue[] initializers = initializerExpression.getInitializers();
            reportTypeDef(context, argument, errorNode, false, initializers, allAnnotations);
        }
    }

    private static void reportTypeDef(@NonNull JavaContext context, @NonNull PsiElement node,
            @Nullable PsiElement errorNode, boolean flag,
            @NonNull PsiAnnotationMemberValue[] allowedValues,
            @NonNull PsiAnnotation[] allAnnotations) {
        if (errorNode == null) {
            errorNode = node;
        }

        String values = listAllowedValues(allowedValues);
        String message;
        if (flag) {
            message = "Must be one or more of: " + values;
        } else {
            message = "Must be one of: " + values;
        }

        PsiAnnotation rangeAnnotation = findIntRange(allAnnotations);
        if (rangeAnnotation != null) {
            // Allow @IntRange on this number
            String rangeError = getIntRangeError(context, rangeAnnotation, node);
            if (rangeError != null && !rangeError.isEmpty()) {
                message += " or " + Character.toLowerCase(rangeError.charAt(0))
                        + rangeError.substring(1);
            }
        }

        report(context, TYPE_DEF, errorNode, context.getLocation(errorNode), message);
    }

    @Nullable
    private static PsiAnnotationMemberValue getAnnotationValue(@NonNull PsiAnnotation annotation) {
        PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
        for (PsiNameValuePair pair : attributes) {
            if (pair.getName() == null || pair.getName().equals(ATTR_VALUE)) {
                return pair.getValue();
            }
        }
        return null;
    }

    private static String listAllowedValues(@NonNull PsiAnnotationMemberValue[] allowedValues) {
        StringBuilder sb = new StringBuilder();
        for (PsiAnnotationMemberValue allowedValue : allowedValues) {
            String s = null;
            if (allowedValue instanceof PsiReference) {
                PsiElement resolved = ((PsiReference) allowedValue).resolve();
                if (resolved instanceof PsiField) {
                    PsiField field = (PsiField) resolved;
                    String containingClassName = field.getContainingClass() != null
                            ? field.getContainingClass().getName() : null;
                    if (containingClassName == null) {
                        continue;
                    }
                    s = containingClassName + "." + field.getName();
                }
            }
            if (s == null) {
                s = allowedValue.getText();
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(s);
        }
        return sb.toString();
    }

    static double getDoubleAttribute(@NonNull PsiAnnotation annotation,
            @NonNull String name, double defaultValue) {
        Double value = getAnnotationDoubleValue(annotation, name);
        if (value != null) {
            return value;
        }

        return defaultValue;
    }

    static long getLongAttribute(@NonNull PsiAnnotation annotation,
            @NonNull String name, long defaultValue) {
        Long value = getAnnotationLongValue(annotation, name);
        if (value != null) {
            return value;
        }

        return defaultValue;
    }

    static boolean getBoolean(@NonNull PsiAnnotation annotation,
            @NonNull String name, boolean defaultValue) {
        Boolean value = getAnnotationBooleanValue(annotation, name);
        if (value != null) {
            return value;
        }

        return defaultValue;
    }

    @NonNull
    static PsiAnnotation[] filterRelevantAnnotations(
            @NonNull JavaEvaluator evaluator, @NonNull PsiAnnotation[] annotations) {
        List<PsiAnnotation> result = null;
        int length = annotations.length;
        if (length == 0) {
            return annotations;
        }
        for (PsiAnnotation annotation : annotations) {
            String signature = annotation.getQualifiedName();
            if (signature == null || signature.startsWith("java.")) {
                // @Override, @SuppressWarnings etc. Ignore
                continue;
            }

            if (signature.startsWith(SUPPORT_ANNOTATIONS_PREFIX)) {
                // Bail on the nullness annotations early since they're the most commonly
                // defined ones. They're not analyzed in lint yet.
                if (signature.endsWith(".Nullable") || signature.endsWith(".NonNull")) {
                    continue;
                }

                // Common case: there's just one annotation; no need to create a list copy
                if (length == 1) {
                    return annotations;
                }
                if (result == null) {
                    result = new ArrayList<>(2);
                }
                result.add(annotation);
            }

            // Special case @IntDef and @StringDef: These are used on annotations
            // themselves. For example, you create a new annotation named @foo.bar.Baz,
            // annotate it with @IntDef, and then use @foo.bar.Baz in your signatures.
            // Here we want to map from @foo.bar.Baz to the corresponding int def.
            // Don't need to compute this if performing @IntDef or @StringDef lookup
            PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
            if (ref == null) {
                continue;
            }
            PsiElement resolved = ref.resolve();
            if (!(resolved instanceof PsiClass) || !((PsiClass)resolved).isAnnotationType()) {
                continue;
            }
            PsiClass cls = (PsiClass)resolved;
            PsiAnnotation[] innerAnnotations = evaluator.getAllAnnotations(cls, false);
            for (int j = 0; j < innerAnnotations.length; j++) {
                PsiAnnotation inner = innerAnnotations[j];
                String a = inner.getQualifiedName();
                if (a == null || a.startsWith("java.")) {
                    // @Override, @SuppressWarnings etc. Ignore
                    continue;
                }
                if (a.equals(INT_DEF_ANNOTATION)
                        || a.equals(PERMISSION_ANNOTATION)
                        || a.equals(INT_RANGE_ANNOTATION)
                        || a.equals(STRING_DEF_ANNOTATION)) {
                    if (length == 1 && j == innerAnnotations.length - 1 && result == null) {
                        return innerAnnotations;
                    }
                    if (result == null) {
                        result = new ArrayList<>(2);
                    }
                    result.add(inner);
                }
            }
        }

        return result != null
                ? result.toArray(PsiAnnotation.EMPTY_ARRAY) : PsiAnnotation.EMPTY_ARRAY;
    }

    // ---- Implements JavaScanner ----

    @Override
    public List<Class<? extends PsiElement>> getApplicablePsiTypes() {
        List<Class<? extends PsiElement>> types = new ArrayList<>(3);
        types.add(PsiArrayAccessExpression.class);
        types.add(PsiCallExpression.class);
        types.add(PsiEnumConstant.class);
        types.add(PsiMethod.class);
        return types;
    }

    @Nullable
    @Override
    public JavaElementVisitor createPsiVisitor(@NonNull JavaContext context) {
        return new CallVisitor(context);
    }

    private class CallVisitor extends JavaElementVisitor {
        private final JavaContext mContext;

        public CallVisitor(JavaContext context) {
            mContext = context;
        }

        // TODO: visitField too such that we can enforce initializer consistency with
        // declared constraints!

        @Override
        public void visitMethod(PsiMethod method) {
            JavaEvaluator evaluator = mContext.getEvaluator();
            PsiAnnotation[] methodAnnotations =
                    filterRelevantAnnotations(evaluator, evaluator.getAllAnnotations(method, true));
            if (methodAnnotations.length > 0) {
                // Check return values
                method.accept(new JavaRecursiveElementVisitor() {
                    @Override
                    public void visitReturnStatement(PsiReturnStatement statement) {
                        PsiExpression returnValue = statement.getReturnValue();
                        if (returnValue != null) {
                            checkAnnotations(mContext, returnValue, returnValue, method,
                                    methodAnnotations, methodAnnotations, PsiAnnotation.EMPTY_ARRAY);
                        }
                        super.visitReturnStatement(statement);
                    }
                });
            }
        }

        @Override
        public void visitCallExpression(PsiCallExpression call) {
            PsiMethod method = call.resolveMethod();
            if (method != null) {
                checkCall(method, call);
            }
        }

        @Override
        public void visitArrayAccessExpression(PsiArrayAccessExpression expression) {
            PsiExpression arrayExpression = expression.getArrayExpression();
            if (arrayExpression instanceof PsiReferenceExpression) {
                PsiElement resolved = ((PsiReferenceExpression) arrayExpression).resolve();
                if (resolved instanceof PsiModifierListOwner) {
                    JavaEvaluator evaluator = mContext.getEvaluator();
                    PsiAnnotation[] methodAnnotations = evaluator.getAllAnnotations((PsiModifierListOwner)resolved, true);
                    methodAnnotations = filterRelevantAnnotations(evaluator, methodAnnotations);
                    if (methodAnnotations.length > 0) {
                        checkContextAnnotations(mContext, null, expression,
                            methodAnnotations);
                    }
                }
            }
        }

        @Override
        public void visitEnumConstant(PsiEnumConstant call) {
            PsiMethod method = call.resolveMethod();
            if (method != null) {
                checkCall(method, call);
            }
        }

        public void checkCall(PsiMethod method, PsiCall call) {
            JavaEvaluator evaluator = mContext.getEvaluator();
            PsiAnnotation[] methodAnnotations = evaluator.getAllAnnotations(method, true);
            methodAnnotations = filterRelevantAnnotations(evaluator, methodAnnotations);

            // Look for annotations on the class as well: these trickle
            // down to all the methods in the class
            PsiClass containingClass = method.getContainingClass();
            PsiAnnotation[] classAnnotations;
            PsiAnnotation[] pkgAnnotations;
            if (containingClass != null) {
                classAnnotations = evaluator.getAllAnnotations(containingClass, true);
                classAnnotations = filterRelevantAnnotations(evaluator, classAnnotations);

                PsiPackage pkg = evaluator.getPackage(containingClass);
                if (pkg != null) {
                    pkgAnnotations = evaluator.getAllAnnotations(pkg, false);
                    pkgAnnotations = filterRelevantAnnotations(evaluator, pkgAnnotations);
                } else {
                    pkgAnnotations = PsiAnnotation.EMPTY_ARRAY;
                }
            } else {
                classAnnotations = PsiAnnotation.EMPTY_ARRAY;
                pkgAnnotations = PsiAnnotation.EMPTY_ARRAY;
            }

            if (methodAnnotations.length > 0) {
                checkAnnotations(mContext, call, call, method, methodAnnotations,
                        methodAnnotations, classAnnotations);

                checkContextAnnotations(mContext, method, call, methodAnnotations);
            }

            if (classAnnotations.length > 0) {
                checkAnnotations(mContext, call, call, method, classAnnotations,
                        methodAnnotations, classAnnotations);

            }

            if (pkgAnnotations.length > 0) {
                checkAnnotations(mContext, call, call, method, pkgAnnotations,
                        methodAnnotations, classAnnotations);
            }

            PsiExpressionList argumentList = call.getArgumentList();
            if (argumentList != null) {
                PsiExpression[] arguments = argumentList.getExpressions();
                PsiParameterList parameterList = method.getParameterList();
                PsiParameter[] parameters = parameterList.getParameters();
                PsiAnnotation[] annotations = null;
                for (int i = 0, n = Math.min(parameters.length, arguments.length);
                        i < n;
                        i++) {
                    PsiExpression argument = arguments[i];
                    PsiParameter parameter = parameters[i];
                    annotations = evaluator.getAllAnnotations(parameter, true);
                    annotations = filterRelevantAnnotations(evaluator, annotations);
                    checkAnnotations(mContext, argument, call, method, annotations,
                            methodAnnotations, classAnnotations);
                }
                if (annotations != null) {
                    // last parameter is varargs (same parameter annotations)
                    for (int i = parameters.length; i < arguments.length; i++) {
                        PsiExpression argument = arguments[i];
                        checkAnnotations(mContext, argument, call, method, annotations,
                                methodAnnotations, classAnnotations);
                    }
                }
            }
        }
    }
}
