package org.gjt.sp.jedit.textarea;

import java.awt.Cursor;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

import javax.swing.event.MouseInputAdapter;

import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.OperatingSystem;
import org.gjt.sp.jedit.TextUtilities;
import org.gjt.sp.jedit.msg.PositionChanging;
import org.gjt.sp.util.StandardUtilities;

public class MouseHandlerBase extends MouseInputAdapter {
	
	//{{{ Private members
		protected final TextArea textArea;
		protected int dragStartLine;
		protected int dragStartOffset;
		protected int dragStart;
		protected int clickCount;
		protected boolean dragged;
		protected boolean quickCopyDrag;
		protected boolean control;
		protected boolean ctrlForRectangularSelection;
		/* with drag and drop on, a mouse down in a selection does not
		immediately deselect */
		protected boolean maybeDragAndDrop;
		
		public MouseHandlerBase(TextArea textArea) {
			this.textArea = textArea;
		}
		
		//{{{ showCursor() method
		protected void showCursor()
		{
			textArea.getPainter().setCursor(
				Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
		} //}}}
		
		//{{{ isMiddleButton() method
		/**
		 * @param modifiers The modifiers flag from a mouse event
		 * @return true if the modifier match the middle button
		 * @since jEdit 4.3pre7
		 */
		public static boolean isMiddleButton(int modifiers)
		{
			if (OperatingSystem.isMacOS())
			{
				if((modifiers & InputEvent.BUTTON1_MASK) != 0)
					return (modifiers & InputEvent.ALT_MASK) != 0;
				else
					return (modifiers & InputEvent.BUTTON2_MASK) != 0;
			}
			else
				return (modifiers & InputEvent.BUTTON2_MASK) != 0;
		} //}}}
		
		//{{{ isPopupTrigger() method
		/**
		 * Returns if the specified event is the popup trigger event.
		 * This implements precisely defined behavior, as opposed to
		 * MouseEvent.isPopupTrigger().
		 * @param evt The event
		 * @since jEdit 4.3pre7
		 */
		public static boolean isPopupTrigger(MouseEvent evt)
		{
			return isRightButton(evt.getModifiers());
		} //}}}
		
		//{{{ isRightButton() method
		/**
		 * @param modifiers The modifiers flag from a mouse event
		 * @return true if the modifier match the right button
		 * @since jEdit 4.3pre7
		 */
		public static boolean isRightButton(int modifiers)
		{
			if (OperatingSystem.isMacOS())
			{
				if((modifiers & InputEvent.BUTTON1_MASK) != 0)
					return (modifiers & InputEvent.CTRL_MASK) != 0;
				else
					return (modifiers & InputEvent.BUTTON3_MASK) != 0;
			}
			else
				return (modifiers & InputEvent.BUTTON3_MASK) != 0;
		} //}}}
		
		@Override
		public void mousePressed(MouseEvent evt)
		{
			showCursor();

			control = (OperatingSystem.isMacOS() && evt.isMetaDown())
				|| (!OperatingSystem.isMacOS() && evt.isControlDown());

//			ctrlForRectangularSelection = textArea.isCtrlForRectangularSelection();
			textAreaHandlerRectangularSelection();
			mouseHandlerRectangularSelection();
			
			// so that Home <mouse click> Home is not the same
			// as pressing Home twice in a row
			textArea.getInputHandler().resetLastActionCount();

			quickCopyDrag = (textArea.isQuickCopyEnabled() &&
				isMiddleButton(evt.getModifiers()));

			if(!quickCopyDrag)
			{
				textArea.requestFocus();
				TextArea.focusedComponent = textArea;
			}

			if(textArea.getBuffer().isLoading())
				return;
			
//			EditBus.send(new PositionChanging(textArea));
			mouseHandlerEditBus();
			
			int x = evt.getX();
			int y = evt.getY();

			dragStart = textArea.xyToOffset(x,y,
				!(textArea.getPainter().isBlockCaretEnabled()
				|| textArea.isOverwriteEnabled()));
			dragStartLine = textArea.getLineOfOffset(dragStart);
			dragStartOffset = dragStart - textArea.getLineStartOffset(
				dragStartLine);

			if(isPopupTrigger(evt)
				&& textArea.getRightClickPopup() != null)
			{
//				if(textArea.isRightClickPopupEnabled())
//					textArea.handlePopupTrigger(evt);
				mouseHandlerTextArea(evt);
				textAreaPopupTriggerHandler(evt);
				return;
			}

			dragged = false;

			textArea.blink = true;
			textArea.invalidateLine(textArea.getCaretLine());

			clickCount = evt.getClickCount();

			if(textArea.isDragEnabled()
				&& textArea.selectionManager.insideSelection(x,y)
				&& clickCount == 1 && !evt.isShiftDown())
			{
				maybeDragAndDrop = true;

				textArea.moveCaretPosition(dragStart,false);
				return;
			}

			maybeDragAndDrop = false;

			if(quickCopyDrag)
			{
				// ignore double clicks of middle button
				doSingleClick(evt);
			}
			else
			{
				switch(clickCount)
				{
				case 1:
					doSingleClick(evt);
					break;
				case 2:
					doDoubleClick();
					break;
				default: //case 3:
					doTripleClick();
					break;
				}
			}
		}
		
		//{{{ doDoubleClick() method
		protected void doDoubleClick()
		{
			// Ignore empty lines
			if(textArea.getLineLength(dragStartLine) == 0)
				return;

			String lineText = textArea.getLineText(dragStartLine);
			String noWordSep = textArea.getBuffer()
				.getStringProperty("noWordSep");
			if(dragStartOffset == textArea.getLineLength(dragStartLine))
				dragStartOffset--;

			boolean joinNonWordChars = textArea.getJoinNonWordChars();
			int wordStart = TextUtilities.findWordStart(lineText,dragStartOffset,
				noWordSep,joinNonWordChars,false,false);
			int wordEnd = TextUtilities.findWordEnd(lineText,
				dragStartOffset+1,noWordSep,
				joinNonWordChars,false,false);

			int lineStart = textArea.getLineStartOffset(dragStartLine);
			Selection sel = new Selection.Range(
				lineStart + wordStart,
				lineStart + wordEnd);
			if(textArea.isMultipleSelectionEnabled())
				textArea.addToSelection(sel);
			else
				textArea.setSelection(sel);

			if(quickCopyDrag)
				quickCopyDrag = false;

			textArea.moveCaretPosition(lineStart + wordEnd,false);

			dragged = true;
		} //}}}
		
		//{{{ doTripleClick() method
		protected void doTripleClick()
		{
			int newCaret = textArea.getLineEndOffset(dragStartLine);
			if(dragStartLine == textArea.getLineCount() - 1)
				newCaret--;

			Selection sel = new Selection.Range(
				textArea.getLineStartOffset(dragStartLine),
				newCaret);
			if(textArea.isMultipleSelectionEnabled())
				textArea.addToSelection(sel);
			else
				textArea.setSelection(sel);

			if(quickCopyDrag)
				quickCopyDrag = false;

			textArea.moveCaretPosition(newCaret,false);

			dragged = true;
		} //}}}
		
		//{{{ doSingleClick() method
		protected void doSingleClick(MouseEvent evt)
		{
			int x = evt.getX();

			int extraEndVirt = 0;
			if(textArea.chunkCache.getLineInfo(
				textArea.getLastScreenLine()).lastSubregion)
			{
				int dragStart = textArea.xyToOffset(x,evt.getY(),
					!textArea.getPainter().isBlockCaretEnabled()
					&& !textArea.isOverwriteEnabled());
				int screenLine = textArea.getScreenLineOfOffset(dragStart);
				ChunkCache.LineInfo lineInfo = textArea.chunkCache.getLineInfo(screenLine);
				int offset = textArea.getScreenLineEndOffset(screenLine);
				if ((1 != offset - dragStart) || (lineInfo.lastSubregion))
				{
					offset--;
				}
				float dragStartLineWidth = textArea.offsetToXY(offset).x;
				if(x > dragStartLineWidth)
				{
					extraEndVirt = (int)(
						(x - dragStartLineWidth)
						/ textArea.charWidth);
					if(!textArea.getPainter().isBlockCaretEnabled()
						&& !textArea.isOverwriteEnabled()
						&& (x - textArea.getHorizontalOffset())
						% textArea.charWidth > textArea.charWidth / 2)
					{
						extraEndVirt++;
					}
				}
			}

			if(((control && ctrlForRectangularSelection) ||
			    textArea.isRectangularSelectionEnabled())
				&& textArea.isEditable())
			{
				int screenLine = (evt.getY() / textArea.getPainter()
					.getFontMetrics().getHeight());
				if(screenLine > textArea.getLastScreenLine())
					screenLine = textArea.getLastScreenLine();
				ChunkCache.LineInfo info = textArea.chunkCache.getLineInfo(screenLine);
				if(info.lastSubregion && extraEndVirt != 0)
				{
					// control-click in virtual space inserts
					// whitespace and moves caret
					String whitespace = StandardUtilities
						.createWhiteSpace(extraEndVirt,0);
					textArea.getBuffer().insert(dragStart,whitespace);

					dragStart += whitespace.length();
				}
			}

			if(evt.isShiftDown())
			{
				// XXX: getMarkPosition() deprecated!
				textArea.resizeSelection(
					textArea.getMarkPosition(),dragStart,extraEndVirt,
					textArea.isRectangularSelectionEnabled()
					|| (control && ctrlForRectangularSelection));

				if(!quickCopyDrag)
					textArea.moveCaretPosition(dragStart,false);

				// so that shift-click-drag works
				dragStartLine = textArea.getMarkLine();
				dragStart = textArea.getMarkPosition();
				dragStartOffset = dragStart
					- textArea.getLineStartOffset(dragStartLine);

				// so that quick copy works
				dragged = true;

				return;
			}

			if(!quickCopyDrag)
				textArea.moveCaretPosition(dragStart,false);

			if(!(textArea.isMultipleSelectionEnabled()
				|| quickCopyDrag))
				textArea.selectNone();
		} //}}}
		
		protected void mouseHandlerRectangularSelection() {
			// Sub-class will implement the logic
		}
		
		protected void mouseHandlerEditBus() {
			// Sub - class will implement the logic
		}
		
		protected void mouseHandlerTextArea(MouseEvent evt) {
			// Sub - class will implement the logic
		}
		
		protected void textAreaHandlerRectangularSelection() {
			// Subclass will implement the logic
		}
		
		protected void textAreaPopupTriggerHandler(MouseEvent evt) {
			
		}
}
