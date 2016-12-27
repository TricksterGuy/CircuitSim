package com.ra4king.circuitsimulator.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.ra4king.circuitsimulator.gui.ComponentManager.ComponentCreator;
import com.ra4king.circuitsimulator.gui.Connection.PortConnection;
import com.ra4king.circuitsimulator.gui.LinkWires.Wire;
import com.ra4king.circuitsimulator.gui.peers.PinPeer;
import com.ra4king.circuitsimulator.simulator.Circuit;
import com.ra4king.circuitsimulator.simulator.CircuitState;
import com.ra4king.circuitsimulator.simulator.Simulator;
import com.ra4king.circuitsimulator.simulator.WireValue;
import com.ra4king.circuitsimulator.simulator.WireValue.State;
import com.ra4king.circuitsimulator.simulator.components.Pin;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/**
 * @author Roi Atalla
 */
public class CircuitManager {
	private Canvas canvas;
	private CircuitBoard circuitBoard;
	
	private Point2D lastMousePosition = new Point2D(0, 0);
	private ComponentPeer potentialComponent;
	private Circuit dummyCircuit = new Circuit(new Simulator());
	private CircuitState dummyCircuitState = new CircuitState(dummyCircuit);
	
	private Connection startConnection, endConnection;
	private Point2D startPoint, curDraggedPoint, draggedDelta;
	private boolean isDraggedHorizontally;
	
	private boolean ctrlDown;
	
	private Map<GuiElement, Point2D> selectedElementsMap = new HashMap<>();
	private boolean selecting;
	
	private ComponentCreator componentCreator;
	
	private String message;
	private long messageSetTime;
	private static final int MESSAGE_POST_DURATION = 5000;
	
	public CircuitManager(Canvas canvas, Simulator simulator) {
		this.canvas = canvas;
		circuitBoard = new CircuitBoard(simulator);
	}
	
	public Circuit getCircuit() {
		return circuitBoard.getCircuit();
	}
	
	public CircuitBoard getCircuitBoard() {
		return circuitBoard;
	}
	
	public Point2D getLastMousePosition() {
		return lastMousePosition;
	}
	
	public void setLastMousePosition(Point2D lastMousePosition) {
		this.lastMousePosition = lastMousePosition;
	}
	
	public void modifiedSelection(ComponentCreator componentCreator) {
		this.componentCreator = componentCreator;
		
		dummyCircuit.getComponents().clear();
		
		if(componentCreator != null) {
			potentialComponent = componentCreator.createComponent(dummyCircuit,
			                                                      GuiUtils.getCircuitCoord(lastMousePosition.getX()),
			                                                      GuiUtils.getCircuitCoord(lastMousePosition.getY()));
			potentialComponent.setX(potentialComponent.getX() - potentialComponent.getWidth() / 2);
			potentialComponent.setY(potentialComponent.getY() - potentialComponent.getHeight() / 2);
		} else {
			potentialComponent = null;
		}
	}
	
	public void repaint() {
		if(Platform.isFxApplicationThread()) {
			paint(canvas.getGraphicsContext2D());
		} else {
			Platform.runLater(() -> paint(canvas.getGraphicsContext2D()));
		}
	}
	
	public void paint(GraphicsContext graphics) {
		graphics.save();
		
		graphics.setFont(Font.font("monospace", 15));
		
		graphics.save();
		graphics.setFill(Color.LIGHTGRAY);
		graphics.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
		
		graphics.setFill(Color.BLACK);
		for(int i = 0; i < canvas.getWidth(); i += GuiUtils.BLOCK_SIZE) {
			for(int j = 0; j < canvas.getHeight(); j += GuiUtils.BLOCK_SIZE) {
				graphics.fillRect(i, j, 1, 1);
			}
		}
		
		graphics.restore();
		
		circuitBoard.paint(graphics);
		
		if(startConnection != null) {
			graphics.save();
			
			graphics.setLineWidth(2);
			graphics.setStroke(Color.GREEN);
			graphics.strokeOval(startConnection.getScreenX() - 2, startConnection.getScreenY() - 2, 10, 10);
			
			if(endConnection != null) {
				graphics.strokeOval(endConnection.getScreenX() - 2, endConnection.getScreenY() - 2, 10, 10);
			}
			
			if(curDraggedPoint != null) {
				int startX = startConnection.getScreenX() + startConnection.getScreenWidth() / 2;
				int startY = startConnection.getScreenY() + startConnection.getScreenHeight() / 2;
				int pointX = GuiUtils.getScreenCircuitCoord(curDraggedPoint.getX());
				int pointY = GuiUtils.getScreenCircuitCoord(curDraggedPoint.getY());
				graphics.setStroke(Color.BLACK);
				if(isDraggedHorizontally) {
					graphics.strokeLine(startX, startY, pointX, startY);
					graphics.strokeLine(pointX, startY, pointX, pointY);
				} else {
					graphics.strokeLine(startX, startY, startX, pointY);
					graphics.strokeLine(startX, pointY, pointX, pointY);
				}
			} else if(startConnection instanceof PortConnection) {
				PortConnection portConnection = (PortConnection)startConnection;
				String name = portConnection.getName();
				if(!name.isEmpty()) {
					Text text = new Text(name);
					text.setFont(graphics.getFont());
					Bounds bounds = text.getLayoutBounds();
					
					double x = startConnection.getScreenX() - bounds.getWidth() / 2 - 3;
					double y = startConnection.getScreenY() + 30;
					double width = bounds.getWidth() + 6;
					double height = bounds.getHeight() + 3;
					
					graphics.setLineWidth(1);
					graphics.setStroke(Color.BLACK);
					graphics.setFill(Color.ORANGE.brighter());
					graphics.fillRect(x, y, width, height);
					graphics.strokeRect(x, y, width, height);
					
					graphics.strokeText(name, x + 3, y + height - 5);
				}
			}
			
			graphics.restore();
		} else if(potentialComponent != null) {
			graphics.save();
			potentialComponent.paint(graphics, dummyCircuitState);
			graphics.restore();
			
			for(Connection connection : potentialComponent.getConnections()) {
				graphics.save();
				connection.paint(graphics, dummyCircuitState);
				graphics.restore();
			}
		} else if(!selecting && startPoint != null) {
			double startX = startPoint.getX() < curDraggedPoint.getX() ? startPoint.getX() : curDraggedPoint.getX();
			double startY = startPoint.getY() < curDraggedPoint.getY() ? startPoint.getY() : curDraggedPoint.getY();
			double width = Math.abs(curDraggedPoint.getX() - startPoint.getX());
			double height = Math.abs(curDraggedPoint.getY() - startPoint.getY());
			
			graphics.setStroke(Color.GREEN.darker());
			graphics.strokeRect(startX, startY, width, height);
		}
		
		for(GuiElement selectedElement : selectedElementsMap.keySet()) {
			graphics.setStroke(Color.RED);
			if(selectedElement instanceof Wire) {
				graphics.strokeRect(selectedElement.getScreenX() - 2, selectedElement.getScreenY() - 2,
				                    selectedElement.getScreenWidth() + 4, selectedElement.getScreenHeight() + 4);
			} else {
				GuiUtils.drawShape(graphics::strokeRect, selectedElement);
			}
		}
		
		if(System.nanoTime() - messageSetTime < MESSAGE_POST_DURATION * 1000000L) {
			Bounds bounds = getBounds(graphics.getFont(), message);
			graphics.setStroke(Color.BLACK);
			graphics.strokeText(message, (canvas.getWidth() - bounds.getWidth()) * 0.5, canvas.getHeight() - 50);
		}
		
		graphics.restore();
	}
	
	private Bounds getBounds(Font font, String string) {
		Text text = new Text(string);
		text.setFont(font);
		return text.getLayoutBounds();
	}
	
	private interface ThrowableRunnable {
		void run() throws Exception;
	}
	
	private void mayThrow(ThrowableRunnable runnable) {
		try {
			runnable.run();
			messageSetTime = 0;
		} catch(Exception exc) {
			message = exc.getMessage();
			messageSetTime = System.nanoTime();
		}
	}
	
	private void reset() {
		isDraggedHorizontally = false;
		startConnection = null;
		endConnection = null;
		startPoint = null;
		curDraggedPoint = null;
		selecting = false;
	}
	
	public void keyPressed(KeyEvent e) {
		switch(e.getCode()) {
			case CONTROL:
				ctrlDown = e.getEventType() != KeyEvent.KEY_RELEASED;
				break;
			case NUMPAD0:
			case NUMPAD1:
			case DIGIT0:
			case DIGIT1:
				int value = e.getText().charAt(0) - '0';
				
				GuiElement selectedElem;
				if(selectedElementsMap.size() == 1 &&
				   (selectedElem = selectedElementsMap.keySet().iterator().next()) instanceof PinPeer) {
					PinPeer selectedPin = (PinPeer)selectedElem;
					WireValue currentValue =
							new WireValue(getCircuit().getTopLevelState()
							                          .getMergedValue(selectedPin.getComponent().getPort(Pin.PORT)));
					
					for(int i = currentValue.getBitSize() - 1; i > 0; i--) {
						currentValue.setBit(i, currentValue.getBit(i - 1));
					}
					currentValue.setBit(0, value == 1 ? State.ONE : State.ZERO);
					selectedPin.getComponent().setValue(getCircuit().getTopLevelState(), currentValue);
					mayThrow(() -> circuitBoard.runSim());
					break;
				}
			case BACK_SPACE:
			case DELETE:
				mayThrow(() -> circuitBoard.removeElements(selectedElementsMap.keySet()));
				selectedElementsMap.clear();
			case ESCAPE:
				if(!selectedElementsMap.isEmpty()) {
					if(draggedDelta.getX() != 0 || draggedDelta.getY() != 0) {
						mayThrow(() -> circuitBoard.moveElements(-(int)draggedDelta.getX(),
						                                         -(int)draggedDelta.getY()));
						mayThrow(() -> circuitBoard.finalizeMove());
						draggedDelta = new Point2D(0, 0);
					}
					selectedElementsMap.clear();
				}
				reset();
				break;
		}
		
		repaint();
	}
	
	public void mousePressed(MouseEvent e) {
		if(startConnection != null) {
			curDraggedPoint = new Point2D(e.getX(), e.getY());
		} else if(potentialComponent != null) {
			if(componentCreator != null) {
				mayThrow(() -> circuitBoard.createComponent(componentCreator, potentialComponent.getX(),
				                                            potentialComponent.getY()));
			}
		} else {
			startPoint = new Point2D(e.getX(), e.getY());
			curDraggedPoint = startPoint;
			draggedDelta = new Point2D(0, 0);
			
			Optional<GuiElement> clickedComponent =
					Stream.concat(circuitBoard.getComponents().stream(),
					              circuitBoard.getLinks()
					                          .stream()
					                          .flatMap(link -> link.getWires().stream()))
					      .filter(peer -> peer.containsScreenCoord((int)e.getX(), (int)e.getY()))
					      .findAny();
			if(clickedComponent.isPresent()) {
				selecting = true;
				GuiElement selectedElement = clickedComponent.get();
				
				if(!ctrlDown && selectedElementsMap.size() == 1) {
					selectedElementsMap.clear();
				}
				
				selectedElementsMap.put(selectedElement, new Point2D(selectedElement.getX(), selectedElement.getY()));
				
				if(selectedElement instanceof PinPeer && ((PinPeer)selectedElement).isInput()) {
					((PinPeer)selectedElement).clicked(getCircuit().getTopLevelState(), (int)e.getX(), (int)e.getY());
					mayThrow(() -> circuitBoard.runSim());
				}
			} else {
				selecting = false;
				selectedElementsMap.clear();
			}
		}
		
		repaint();
	}
	
	public void mouseReleased(MouseEvent e) {
		if(selecting && !selectedElementsMap.isEmpty()) {
			mayThrow(() -> circuitBoard.finalizeMove());
			draggedDelta = new Point2D(0, 0);
		}
		
		if(curDraggedPoint != null && startConnection != null) {
			int endMidX = endConnection == null
			              ? GuiUtils.getCircuitCoord(curDraggedPoint.getX())
			              : endConnection.getX();
			int endMidY = endConnection == null
			              ? GuiUtils.getCircuitCoord(curDraggedPoint.getY())
			              : endConnection.getY();
			
			if(endMidX - startConnection.getX() != 0 && endMidY - startConnection.getY() != 0) {
				if(isDraggedHorizontally) {
					mayThrow(() -> circuitBoard.addWire(startConnection.getX(), startConnection.getY(),
					                                    endMidX - startConnection.getX(), true));
					mayThrow(() -> circuitBoard.addWire(endMidX, startConnection.getY(),
					                                    endMidY - startConnection.getY(), false));
				} else {
					mayThrow(() -> circuitBoard.addWire(startConnection.getX(), startConnection.getY(),
					                                    endMidY - startConnection.getY(), false));
					mayThrow(() -> circuitBoard.addWire(startConnection.getX(), endMidY,
					                                    endMidX - startConnection.getX(), true));
				}
			} else if(endMidX - startConnection.getX() != 0) {
				mayThrow(() -> circuitBoard.addWire(startConnection.getX(), startConnection.getY(),
				                                    endMidX - startConnection.getX(), true));
			} else if(endMidY - startConnection.getY() != 0) {
				mayThrow(() -> circuitBoard.addWire(endMidX, startConnection.getY(), endMidY - startConnection.getY(),
				                                    false));
			}
		}
		
		reset();
		mouseMoved(e);
		repaint();
	}
	
	public void mouseDragged(MouseEvent e) {
		Point2D curPos = new Point2D(e.getX(), e.getY());
		
		if(selecting && !selectedElementsMap.isEmpty()) {
			Point2D diff = curPos.subtract(startPoint).multiply(1.0 / GuiUtils.BLOCK_SIZE);
			int dx = (int)(diff.getX() - draggedDelta.getX());
			int dy = (int)(diff.getY() - draggedDelta.getY());
			
			if(dx != 0 || dy != 0) {
				if(draggedDelta.getX() == 0 && draggedDelta.getY() == 0) {
					mayThrow(() -> circuitBoard.initMove(selectedElementsMap.keySet()));
				}
				
				mayThrow(() -> circuitBoard.moveElements(dx, dy));
				draggedDelta = draggedDelta.add(dx, dy);
			}
		} else if(startPoint != null) {
			int startX = (int)(startPoint.getX() < curDraggedPoint.getX() ? startPoint.getX()
			                                                              : curDraggedPoint.getX());
			int startY = (int)(startPoint.getY() < curDraggedPoint.getY() ? startPoint.getY()
			                                                              : curDraggedPoint.getY());
			int width = (int)Math.abs(curDraggedPoint.getX() - startPoint.getX());
			int height = (int)Math.abs(curDraggedPoint.getY() - startPoint.getY());
			
			selectedElementsMap = Stream.concat(circuitBoard.getComponents().stream(),
			                                    circuitBoard.getLinks()
			                                                .stream()
			                                                .flatMap(link -> link.getWires().stream()))
			                            .filter(peer -> peer.isWithinScreenCoord(startX, startY, width, height))
			                            .collect(Collectors.toMap(peer -> peer,
			                                                      peer -> new Point2D(peer.getX(), peer.getY())));
		}
		
		if(curDraggedPoint != null) {
			if(startConnection != null) {
				int currDiffX = GuiUtils.getCircuitCoord(e.getX()) - startConnection.getX();
				int prevDiffX = GuiUtils.getCircuitCoord(curDraggedPoint.getX()) - startConnection.getX();
				int currDiffY = GuiUtils.getCircuitCoord(e.getY()) - startConnection.getY();
				int prevDiffY = GuiUtils.getCircuitCoord(curDraggedPoint.getY()) - startConnection.getY();
				
				if(currDiffX == 0 || prevDiffX == 0 ||
				   currDiffX / Math.abs(currDiffX) != prevDiffX / Math.abs(prevDiffX)) {
					isDraggedHorizontally = false;
				}
				
				if(currDiffY == 0 || prevDiffY == 0 ||
				   currDiffY / Math.abs(currDiffY) != prevDiffY / Math.abs(prevDiffY)) {
					isDraggedHorizontally = true;
				}
			}
			
			curDraggedPoint = curPos;
			endConnection = circuitBoard.findConnection(
					GuiUtils.getCircuitCoord(curDraggedPoint.getX()),
					GuiUtils.getCircuitCoord(curDraggedPoint.getY()));
		}
		
		repaint();
	}
	
	public void mouseMoved(MouseEvent e) {
		boolean repaint = false;
		lastMousePosition = new Point2D(e.getX(), e.getY());
		if(potentialComponent != null) {
			potentialComponent.setX(GuiUtils.getCircuitCoord(e.getX()) - potentialComponent.getWidth() / 2);
			potentialComponent.setY(GuiUtils.getCircuitCoord(e.getY()) - potentialComponent.getHeight() / 2);
			repaint = true;
		}
		
		Set<Connection> selectedConns = circuitBoard.getConnections(GuiUtils.getCircuitCoord(e.getX()),
		                                                            GuiUtils.getCircuitCoord(e.getY()));
		
		Connection selected = null;
		
		for(Connection connection : selectedConns) {
			if(connection instanceof PortConnection) {
				selected = connection;
				break;
			}
		}
		
		if(selected == null && !selectedConns.isEmpty()) {
			selected = selectedConns.iterator().next();
		}
		
		if(selected != startConnection) {
			startConnection = selected;
			repaint = true;
		}
		
		if(repaint) repaint();
	}
}