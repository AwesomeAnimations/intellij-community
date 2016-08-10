/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.application.options.colors;

import com.intellij.application.options.colors.highlighting.HighlightData;
import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorSchemeAttributeDescriptor;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.openapi.options.colors.RainbowColorSettingsPage;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

class CustomizedSwitcherPanel extends JPanel implements OptionsPanelImpl.ColorDescriptionPanel {
  private ColorSettingsPage myPage;

  private PreviewPanel myPreviewPanel;
  private ColorAndFontDescriptionPanel myColorAndFontPanel;
  private RainbowDescriptionPanel myRainbowPanel;

  private OptionsPanelImpl.ColorDescriptionPanel myActive;

  public CustomizedSwitcherPanel(ColorAndFontGlobalState options, @Nullable PreviewPanel previewPanel, ColorSettingsPage page) {
    super();
    myPage = page;
    myPreviewPanel = previewPanel;

    myRainbowPanel = new RainbowDescriptionPanel(options);
    myColorAndFontPanel = new ColorAndFontDescriptionPanel();

    Dimension sizeR = myRainbowPanel.getPreferredSize();
    Dimension sizeC = myColorAndFontPanel.getPreferredSize();
    Dimension preferredSize = new Dimension();
    preferredSize.setSize(Math.max(sizeR.getWidth(), sizeC.getWidth()),
                          Math.max(sizeR.getHeight(), sizeC.getHeight()));
    setPreferredSize(preferredSize);
  }

  @NotNull
  @Override
  public JComponent getPanel() {
    return this;
  }

  @Override
  public void resetDefault() {
    if (myActive != null) {
      final PaintLocker locker = new PaintLocker(this);
      try {
        setPreferredSize(getSize());// froze [this] size
        remove(myActive.getPanel());
        myActive = null;
      }
      finally {
        locker.release();
      }
    }
  }

  @Override
  public void reset(@NotNull EditorSchemeAttributeDescriptor descriptor) {
    JComponent oldPanel = myActive == null ? null : myActive.getPanel();
    myActive = getPanelForDescriptor(descriptor);
    JComponent newPanel = myActive == null ? null : myActive.getPanel();

    if (oldPanel != newPanel) {
      final PaintLocker locker = new PaintLocker(this);
      try {
        if (oldPanel != null) {
          remove(oldPanel);
        }
        if (newPanel != null) {
          setPreferredSize(null);// make [this] resizable
          add(newPanel);
        }
      }
      finally {
        locker.release();
      }
    }
    if (myActive != null) {
      myActive.reset(descriptor);
    }
    updatePreviewPanel(descriptor);
  }

  protected OptionsPanelImpl.ColorDescriptionPanel getPanelForDescriptor(@NotNull EditorSchemeAttributeDescriptor descriptor) {
    if (descriptor instanceof RainbowAttributeDescriptor) {
      return myRainbowPanel;
    }
    else if (descriptor instanceof ColorAndFontDescription) {
      return myColorAndFontPanel;
    }
    return null;
  }

  private void addRainbowHighlighting(@NotNull DocumentEx document,
                                      @Nullable List<HighlightData> showLineData,
                                      @NotNull List<HighlightData> data,
                                      @NotNull RainbowHighlighter rainbowHighlighter,
                                      @NotNull List<TextAttributesKey> rainbowTempKeys) {
    if (!rainbowTempKeys.isEmpty()) {
      List<HighlightData> newData = new ArrayList<HighlightData>();
      if (showLineData != null) newData.addAll(showLineData);

      HashMap<String, Integer> id2index = new HashMap<String, Integer>();

      for (HighlightData d : data) {
        if (((RainbowColorSettingsPage)myPage).isRainbowType(d.getHighlightKey())) {
          String id = document.getText(TextRange.create(d.getStartOffset(), d.getEndOffset()));

          int index = rainbowHighlighter.getColorIndex(id2index, id, RainbowHighlighter.getRainbowHash(id));
          HighlightData rainbow = new HighlightData(d.getStartOffset(), d.getEndOffset(), rainbowTempKeys.get(index));

          //fixme: twisted coloring in editor. We need add rainbow-tag twice.
          newData.add(rainbow);
          newData.add(d);
          newData.add(rainbow);
        }
        else if (!RainbowHighlighter.isRainbowTempKey(d.getHighlightKey())) {
          newData.add(d);
        }
      }
      data.clear();
      data.addAll(newData);
    }
  }

  private static void removeRainbowHighlighting(@NotNull List<HighlightData> data) {
    List<TextAttributesKey> keys = RainbowHighlighter.getRainbowKeys();
    if (!keys.isEmpty()) {
      List<HighlightData> newData = new ArrayList<HighlightData>();
      for (HighlightData d : data) {
        if (!keys.contains(d.getHighlightKey())) {
          newData.add(d);
        }
      }
      data.clear();
      data.addAll(newData);
    }
  }

  @Override
  public void apply(@NotNull EditorSchemeAttributeDescriptor descriptor, EditorColorsScheme scheme) {
    if (myActive != null) {
      myActive.apply(descriptor, scheme);
      updatePreviewPanel(descriptor);
    }
  }

  protected void updatePreviewPanel(@NotNull EditorSchemeAttributeDescriptor descriptor) {
    if (!(myPreviewPanel instanceof SimpleEditorPreview)) return;
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> ApplicationManager.getApplication().runWriteAction(() -> {
      SimpleEditorPreview simpleEditorPreview = (SimpleEditorPreview)myPreviewPanel;
      String demoText = (myPage instanceof RainbowColorSettingsPage
                         && descriptor instanceof RainbowAttributeDescriptor)
                        ? ((RainbowColorSettingsPage)myPage).getRainbowDemoText()
                        : myPage.getDemoText();
      List<HighlightData> showLineData = null;

      if (myPage instanceof RainbowColorSettingsPage && myRainbowPanel.myGlobalState.isRainbowOn) {
        RainbowHighlighter highlighter = new RainbowHighlighter(descriptor.getScheme());
        List<TextAttributesKey> tempKeys = highlighter.getRainbowTempKeys();
        EditorEx editor = simpleEditorPreview.getEditor();
        if (myActive == myRainbowPanel) {
          Pair<String, List<HighlightData>> demo = getColorDemoLine(highlighter, tempKeys);
          simpleEditorPreview.setDemoText(demo.first + "\n" + demoText);
          showLineData = demo.second;
        }
        else {
          simpleEditorPreview.setDemoText(demoText);
        }
        addRainbowHighlighting(editor.getDocument(),
                               showLineData,
                               simpleEditorPreview.getHighlightDataForExtension(),
                               highlighter,
                               tempKeys);
      }
      else {
        simpleEditorPreview.setDemoText(demoText);
        removeRainbowHighlighting(simpleEditorPreview.getHighlightDataForExtension());
      }

      simpleEditorPreview.updateView();
      if (descriptor instanceof RainbowAttributeDescriptor) {
        simpleEditorPreview.scrollHighlightInView(showLineData);
      }
    }));
  }

  @NotNull
  private static Pair<String, List<HighlightData>> getColorDemoLine(RainbowHighlighter highlighter, List<TextAttributesKey> tempKeys) {
    int colorsCount = highlighter.getColorsCount();
    int stopCount = RainbowHighlighter.getRainbowKeys().size();
    List<HighlightData> markup = new ArrayList<HighlightData>(colorsCount);
    StringBuilder sb = new StringBuilder();
    int pos = 0;
    int i = 0;
    for (TextAttributesKey key : tempKeys) {
      String toAdd = (i % stopCount == 0) ? "Stop#" + String.valueOf(i / stopCount + 1) : "T";
      int end = pos + toAdd.length();
      markup.add(new HighlightData(pos, end, key));
      if (sb.length() != 0) {
        sb.append(" ");
      }
      sb.append(toAdd);
      pos = end + 1;
      ++i;
    }
    return Pair.create(sb.toString(), markup);
  }

  @Override
  public void addListener(@NotNull Listener listener) {
    myRainbowPanel.addListener(listener);
    myColorAndFontPanel.addListener(listener);
  }

  private static class PaintLocker {
    private Container myPaintHolder;
    private boolean myPaintState;

    public PaintLocker(@NotNull JComponent component) {
      myPaintHolder = component.getParent();
      myPaintState = myPaintHolder.getIgnoreRepaint();
      myPaintHolder.setIgnoreRepaint(true);
    }

    public void release() {
      myPaintHolder.validate();
      myPaintHolder.setIgnoreRepaint(myPaintState);
      myPaintHolder.repaint();
    }
  }
}
