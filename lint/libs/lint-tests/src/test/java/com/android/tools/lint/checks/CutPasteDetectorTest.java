/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.tools.lint.detector.api.Detector;

public class CutPasteDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new CutPasteDetector();
    }

    public void test() {
        String expected = ""
                + "src/test/pkg/PasteError.java:22: Warning: The id R.id.duplicated has already been looked up in this method; possible cut & paste error? [CutPasteId]\n"
                + "        View view2 = findViewById(R.id.duplicated);\n"
                + "                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "    src/test/pkg/PasteError.java:21: First usage here\n"
                + "src/test/pkg/PasteError.java:78: Warning: The id R.id.duplicated has already been looked up in this method; possible cut & paste error? [CutPasteId]\n"
                + "            view2 = findViewById(R.id.duplicated);\n"
                + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "    src/test/pkg/PasteError.java:75: First usage here\n"
                + "src/test/pkg/PasteError.java:85: Warning: The id R.id.duplicated has already been looked up in this method; possible cut & paste error? [CutPasteId]\n"
                + "            view2 = findViewById(R.id.duplicated);\n"
                + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "    src/test/pkg/PasteError.java:83: First usage here\n"
                + "src/test/pkg/PasteError.java:93: Warning: The id R.id.duplicated has already been looked up in this method; possible cut & paste error? [CutPasteId]\n"
                + "            view2 = findViewById(R.id.duplicated);\n"
                + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "    src/test/pkg/PasteError.java:90: First usage here\n"
                + "src/test/pkg/PasteError.java:102: Warning: The id R.id.duplicated has already been looked up in this method; possible cut & paste error? [CutPasteId]\n"
                + "                view2 = findViewById(R.id.duplicated);\n"
                + "                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "    src/test/pkg/PasteError.java:98: First usage here\n"
                + "src/test/pkg/PasteError.java:148: Warning: The id R.id.duplicated has already been looked up in this method; possible cut & paste error? [CutPasteId]\n"
                + "                TextView sectionTitleView = (TextView) root.findViewById(R.id.duplicated);\n"
                + "                                                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "    src/test/pkg/PasteError.java:144: First usage here\n"
                + "src/test/pkg/PasteError.java:162: Warning: The id R.id.duplicated has already been looked up in this method; possible cut & paste error? [CutPasteId]\n"
                + "        TextView sectionTitleView = (TextView) root.findViewById(R.id.duplicated);\n"
                + "                                               ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "    src/test/pkg/PasteError.java:158: First usage here\n"
                + "src/test/pkg/PasteError.java:171: Warning: The id R.id.duplicated has already been looked up in this method; possible cut & paste error? [CutPasteId]\n"
                + "                    view2 = findViewById(R.id.duplicated);\n"
                + "                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "    src/test/pkg/PasteError.java:168: First usage here\n"
                + "0 errors, 8 warnings\n";

        //noinspection all // Sample code
        lint().files(
                java("src/test/pkg/PasteError.java", ""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import android.annotation.SuppressLint;\n"
                        + "import android.app.Activity;\n"
                        + "import android.view.LayoutInflater;\n"
                        + "import android.view.View;\n"
                        + "import android.view.ViewGroup;\n"
                        + "import android.widget.Button;\n"
                        + "import android.widget.TextView;\n"
                        + "\n"
                        + "@SuppressWarnings({\"ConstantConditions\", \"UnnecessaryLocalVariable\", \"ConstantIfStatement\",\n"
                        + "        \"StatementWithEmptyBody\", \"FieldCanBeLocal\", \"unused\", \"UnusedAssignment\"})\n"
                        + "public class PasteError extends Activity {\n"
                        + "    protected void ok() {\n"
                        + "        Button button1 = (Button) findViewById(R.id.textView1);\n"
                        + "        mView2 = findViewById(R.id.textView2);\n"
                        + "        View view3 = findViewById(R.id.activity_main);\n"
                        + "    }\n"
                        + "\n"
                        + "    protected void error() {\n"
                        + "        View view1 = findViewById(R.id.duplicated);\n"
                        + "        View view2 = findViewById(R.id.duplicated);\n"
                        + "        View view3 = findViewById(R.id.ok);\n"
                        + "    }\n"
                        + "\n"
                        + "    protected void ok2() {\n"
                        + "        View view1;\n"
                        + "        if (true) {\n"
                        + "            view1 = findViewById(R.id.ok);\n"
                        + "        } else {\n"
                        + "            view1 = findViewById(R.id.ok);\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    @SuppressLint(\"CutPasteId\")\n"
                        + "    protected void suppressed() {\n"
                        + "        View view1 = findViewById(R.id.duplicated);\n"
                        + "        View view2 = findViewById(R.id.duplicated);\n"
                        + "    }\n"
                        + "\n"
                        + "    private void ok3() {\n"
                        + "        if (view == null || view.findViewById(R.id.city_name) == null) {\n"
                        + "            view = mInflater.inflate(R.layout.city_list_item, parent, false);\n"
                        + "        }\n"
                        + "        TextView name = (TextView) view.findViewById(R.id.city_name);\n"
                        + "    }\n"
                        + "\n"
                        + "    private void ok4() {\n"
                        + "        mPrevAlbumWrapper = mPrevTrackLayout.findViewById(R.id.album_wrapper);\n"
                        + "        mNextAlbumWrapper = mNextTrackLayout.findViewById(R.id.album_wrapper);\n"
                        + "    }\n"
                        + "\n"
                        + "    public View getView(int position, View convertView, ViewGroup parent) {\n"
                        + "        View listItem = convertView;\n"
                        + "        if (getItemViewType(position) == VIEW_TYPE_HEADER) {\n"
                        + "            TextView header = (TextView) listItem.findViewById(R.id.name);\n"
                        + "        } else if (getItemViewType(position) == VIEW_TYPE_BOOLEAN) {\n"
                        + "            TextView filterName = (TextView) listItem.findViewById(R.id.name);\n"
                        + "        } else {\n"
                        + "            TextView filterName = (TextView) listItem.findViewById(R.id.name);\n"
                        + "        }\n"
                        + "        return null;\n"
                        + "    }\n"
                        + "\n"
                        + "    protected void ok_branch_1() {\n"
                        + "        if (true) {\n"
                        + "            view1 = findViewById(R.id.ok);\n"
                        + "        } else {\n"
                        + "            view2 = findViewById(R.id.ok);\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    protected void error_branch_1() {\n"
                        + "        if (true) {\n"
                        + "            view1 = findViewById(R.id.duplicated);\n"
                        + "        }\n"
                        + "        if (true) {\n"
                        + "            view2 = findViewById(R.id.duplicated);\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    protected void error_branch_2() {\n"
                        + "        view1 = findViewById(R.id.duplicated);\n"
                        + "        if (true) {\n"
                        + "            view2 = findViewById(R.id.duplicated);\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    protected void error_branch_3() {\n"
                        + "        view1 = findViewById(R.id.duplicated);\n"
                        + "        if (true) {\n"
                        + "        } else {\n"
                        + "            view2 = findViewById(R.id.duplicated);\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    protected void error_branch_4() {\n"
                        + "        view1 = findViewById(R.id.duplicated);\n"
                        + "        if (true) {\n"
                        + "        } else {\n"
                        + "            if (true) {\n"
                        + "                view2 = findViewById(R.id.duplicated);\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    protected void ok_branch_2() {\n"
                        + "        if (true) {\n"
                        + "            view1 = findViewById(R.id.ok);\n"
                        + "        } else {\n"
                        + "            if (true) {\n"
                        + "                view2 = findViewById(R.id.ok);\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    protected void ok_branch3() {\n"
                        + "        if (true) {\n"
                        + "            view1 = findViewById(R.id.ok);\n"
                        + "            return;\n"
                        + "        }\n"
                        + "        if (true) {\n"
                        + "            view2 = findViewById(R.id.ok);\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    public static void ok_switch(View root, int position) {\n"
                        + "        // mutually exclusive branches\n"
                        + "        switch (position) {\n"
                        + "            case 0: {\n"
                        + "                TextView titleView = (TextView) root.findViewById(R.id.ok);\n"
                        + "            }\n"
                        + "            break;\n"
                        + "            default: {\n"
                        + "                TextView sectionTitleView = (TextView) root.findViewById(R.id.ok);\n"
                        + "            }\n"
                        + "            break;\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    public static void error_switch_fallthrough(View root, int position) {\n"
                        + "        switch (position) {\n"
                        + "            case 0: {\n"
                        + "                TextView titleView = (TextView) root.findViewById(R.id.duplicated);\n"
                        + "                // fallthrough!\n"
                        + "            }\n"
                        + "            default: {\n"
                        + "                TextView sectionTitleView = (TextView) root.findViewById(R.id.duplicated);\n"
                        + "            }\n"
                        + "            break;\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    public static void warning_switch_to_outer(View root, int position) {\n"
                        + "        switch (position) {\n"
                        + "            case 0:\n"
                        + "            {\n"
                        + "                TextView titleView = (TextView) root.findViewById(R.id.duplicated);\n"
                        + "            }\n"
                        + "            break;\n"
                        + "        }\n"
                        + "        TextView sectionTitleView = (TextView) root.findViewById(R.id.duplicated);\n"
                        + "    }\n"
                        + "\n"
                        + "    public void while_loop_error(View root, int position) {\n"
                        + "        while (position-- > 0) { // here we can flow back\n"
                        + "            if (true) {\n"
                        + "                view1 = findViewById(R.id.duplicated);\n"
                        + "            } else {\n"
                        + "                if (true) {\n"
                        + "                    view2 = findViewById(R.id.duplicated);\n"
                        + "                }\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    private View view1;\n"
                        + "    private View mView2;\n"
                        + "    private View view;\n"
                        + "    private View view2;\n"
                        + "    private LayoutInflater mInflater;\n"
                        + "    private Object mPrevAlbumWrapper;\n"
                        + "    private Object mNextAlbumWrapper;\n"
                        + "    private Activity mPrevTrackLayout;\n"
                        + "    private Activity mNextTrackLayout;\n"
                        + "    private android.view.ViewGroup parent;\n"
                        + "    private static final int VIEW_TYPE_HEADER = 1;\n"
                        + "    private static final int VIEW_TYPE_BOOLEAN = 2;\n"
                        + "    private int getItemViewType(int position) {\n"
                        + "        return VIEW_TYPE_BOOLEAN;\n"
                        + "    }\n"
                        + "    public static final class R {\n"
                        + "        public static final class id {\n"
                        + "            public static final int ok = 0x7f0a0000;\n"
                        + "            public static final int duplicated = 0x7f0a0000;\n"
                        + "            public static final int textView1 = 0x7f0a0001;\n"
                        + "            public static final int textView2 = 0x7f0a0002;\n"
                        + "            public static final int activity_main = 0x7f0a0003;\n"
                        + "            public static final int album_wrapper = 0x7f0a0004;\n"
                        + "            public static final int city_name = 0x7f0a0005;\n"
                        + "            public static final int name = 0x7f0a0006;\n"
                        + "        }\n"
                        + "        public static final class layout {\n"
                        + "            public static final int city_list_item = 0x7f0b0000;\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"))
                .run()
                .expect(expected);
    }

    public void testCompareFindViewById() {
        //noinspection all // Sample code
        lint().files(
                java(""
                        + "package test.pkg;\n"
                        + "import android.app.Activity;\n"
                        + "import android.view.LayoutInflater;\n"
                        + "import android.view.View;\n"
                        + "\n"
                        + "public class FindViewByIdTest extends Activity {\n"
                        + "    private boolean mScrollingHeroView;\n"
                        + "    private HeroViewHolder mHeroViewHolder;\n"
                        + "\n"
                        + "    void test() {\n"
                        + "        mScrollingHeroView = findViewById(R.id.alarm_hero_view) == null;\n"
                        + "\n"
                        + "        if (mScrollingHeroView) {\n"
                        + "            mHeroViewHolder = new HeroViewHolder(LayoutInflater.from(this)\n"
                        + "                    .inflate(R.layout.alarm_card_hero_view, this, false));\n"
                        + "        } else {\n"
                        + "            mHeroViewHolder = new HeroViewHolder(findViewById(R.id.alarm_hero_view));\n"
                        + "        }        \n"
                        + "    }\n"
                        + "\n"
                        + "    private static class HeroViewHolder {\n"
                        + "        public HeroViewHolder(View view) {\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"),
                java(""
                        + "package test.pkg;\n"
                        + "public final class R {\n"
                        + "    public static final class id {\n"
                        + "        public static final int alarm_hero_view = 0x7f0a0000;\n"
                        + "    }\n"
                        + "    public static final class layout {\n"
                        + "        public static final int alarm_card_hero_view = 0x7f0b0000;\n"
                        + "    }\n"
                        + "}\n"

                        + ""))
                .run()
                .expectClean();
    }
}
