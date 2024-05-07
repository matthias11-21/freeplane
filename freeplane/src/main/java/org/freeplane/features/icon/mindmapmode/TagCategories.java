/*
 * Created on 27 Apr 2024
 *
 * author dimitry
 */
package org.freeplane.features.icon.mindmapmode;

import java.awt.Color;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.freeplane.core.util.ColorUtils;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.icon.CategorizedTag;
import org.freeplane.features.icon.IconRegistry;
import org.freeplane.features.icon.Tag;

class TagCategories {
    private final IconRegistry registry = new IconRegistry();
    final DefaultTreeModel nodes;
    private final TreeInverseMap<Tag> nodesByTags;
    private File tagCategoryFile;

    TagCategories(final File tagCategoryFile){
        this.tagCategoryFile = tagCategoryFile;
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(TextUtils.getRawText("tags"));
        nodes = new DefaultTreeModel(rootNode) {

            @Override
            public void valueForPathChanged(TreePath path, Object newValue) {
                nodesByTags.valueForPathChanged(path, (Tag)newValue);
                super.valueForPathChanged(path, newValue);
            }

        };
        nodesByTags = new TreeInverseMap(nodes);
        load();
    }

    TagCategories(DefaultTreeModel nodes, File tagCategoryFile) {
        super();
        this.nodes = nodes;
        nodesByTags = new TreeInverseMap(nodes);
        this.tagCategoryFile = tagCategoryFile;
    }



    static void writeTagCategories(DefaultMutableTreeNode node, String indent,
            Writer writer) throws IOException {
        Object userObject = node.getUserObject();
        if (userObject instanceof Tag) {
            Tag tag = (Tag) userObject;
            writeTag(tag, indent, writer);
            indent = indent + " ";
        }
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) node.getChildAt(i);
            writeTagCategories(childNode, indent, writer);
        }
    }

    static void writeTag(Tag tag, String indent, Writer writer)
            throws IOException {
        writer.append(indent + tag.getContent());
        writer.append(ColorUtils.colorToRGBAString(tag.getIconColor()));
        writer.append("\n");
    }

    boolean isEmpty() {
        return getRootNode().isLeaf();
    }

    void readTagCategories(DefaultMutableTreeNode parentNode, Scanner scanner) {
        DefaultMutableTreeNode lastNode = parentNode;
        int lastIndentation = -1;

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            int indentation = getIndentationLevel(line);

            DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(readTag(line.trim()));

            if (indentation == lastIndentation) {
                DefaultMutableTreeNode parent = (DefaultMutableTreeNode) lastNode.getParent();
                addNode(parent, newNode);
            } else if (indentation > lastIndentation) {
                addNode(lastNode, newNode);
            } else {
                DefaultMutableTreeNode parent = (DefaultMutableTreeNode) lastNode.getParent();
                for (int i = 0; i < (lastIndentation - indentation); i++) {
                    parent = (DefaultMutableTreeNode) parent.getParent();
                }
                addNode(parent, newNode);
            }

            lastNode = newNode;
            lastIndentation = indentation;
        }
    }

    private void addNode(DefaultMutableTreeNode parent, DefaultMutableTreeNode newChild) {
        parent.add(newChild);
        nodes.nodesWereInserted(parent, new int[] {parent.getChildCount() - 1});
    }

    private int getIndentationLevel(String line) {
        int indentation = 0;
        while (line.charAt(indentation) == ' ') {
            indentation++;
        }
        return indentation;
    }

    private Tag readTag(String spec) {
        int colorIndex = spec.length() - 9;
        if(colorIndex > 0 && spec.charAt(colorIndex) == '#') {
            String content = spec.substring(0, colorIndex);
            String colorSpec = spec.substring(colorIndex);
            try {
                return registry.setTagColor(content, colorSpec);
            } catch (NumberFormatException e) {
                LogUtils.severe(e);
            }
        }
        return registry.createTag(spec);
    }

    private void load() {
        try (Scanner scanner = new Scanner(tagCategoryFile)){
            readTagCategories(getRootNode(), scanner);
        } catch (FileNotFoundException e1) {/**/}
    }

    DefaultMutableTreeNode getRootNode() {
        return (DefaultMutableTreeNode) nodes.getRoot();
    }

    IconRegistry getRegistry() {
        return registry;
    }

    void addTreeModelListener(TreeModelListener treeModelListener) {
       nodes.addTreeModelListener(treeModelListener);
    }

    void removeTreeModelListener(TreeModelListener l) {
        nodes.removeTreeModelListener(l);
    }

    TreeNode[] addChildNode(MutableTreeNode parent) {
        if(parent == null)
            parent = getRootNode();
        DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(Tag.EMPTY_TAG);
        nodes.insertNodeInto(newNode, parent, parent.getChildCount());
        return nodes.getPathToRoot(newNode);
   }

    TreeNode[] addSiblingNode(MutableTreeNode node) {
        TreeNode[] nothing = {};
        if(node == null)
            return nothing;
        MutableTreeNode parent = (MutableTreeNode) node.getParent();
        if(parent == null)
            return nothing;
        DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(Tag.EMPTY_TAG);
        nodes.insertNodeInto(newNode, parent, parent.getIndex(node) + 1);
        return nodes.getPathToRoot(newNode);
   }

    void removeNodeFromParent(MutableTreeNode node) {
        if(node.getParent() != null)
            nodes.removeNodeFromParent(node);
    }

    void save() {
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(tagCategoryFile))){
            writeTagCategories(getRootNode(), "", writer);
        } catch (IOException e) {
            LogUtils.severe(e);
        }
    }

    void tagChanged(Tag tag) {
        tagChanged(getRootNode(), tag);
    }

    private void tagChanged(DefaultMutableTreeNode node, Tag updatedTag) {
        int childCount = node.getChildCount();
        Object userObject = node.getUserObject();
        if (userObject == updatedTag) {
            nodes.nodeChanged(node);
        }
        for (int i = 0; i < childCount; i++) {
            DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) node.getChildAt(i);
            tagChanged(childNode, updatedTag);
        }
    }

    void add(DefaultMutableTreeNode parent, String data) {
        if (parent == null) {
            parent = getRootNode();
        }
        try(Scanner st = new Scanner(new StringReader(data))){
            readTagCategories(parent, st);
        }
    }

    Optional<Color> getColor(Tag tag) {
        return registry.getTagColor(tag);
    }

    List<CategorizedTag> categorizedTags(IconRegistry iconRegistry){
        final DefaultMutableTreeNode rootNode = getRootNode();
        final Enumeration<?> breadthFirstEnumeration = rootNode.breadthFirstEnumeration();
        breadthFirstEnumeration.nextElement();
        final LinkedList<CategorizedTag> tags = new LinkedList<>();
        while(breadthFirstEnumeration.hasMoreElements()) {
            final DefaultMutableTreeNode node = (DefaultMutableTreeNode) breadthFirstEnumeration.nextElement();
            final Tag tag = (Tag) node.getUserObject();
            tags.add(new CategorizedTagForCategoryNode(node, iconRegistry.getTag(tag)));
        }
        return tags;
    }

    List<CategorizedTag> categorizedTags(List<Tag> tags, IconRegistry iconRegistry){
        final LinkedList<CategorizedTag> categorizedTags = new LinkedList<>();
        for(Tag tag : tags) {
            if(tag.isEmpty())
                categorizedTags.add(CategorizedTag.EMPTY_TAG);
            else {
                for(DefaultMutableTreeNode node : nodesByTags.getNodes(tag))
                    categorizedTags.add(new CategorizedTagForCategoryNode(node, iconRegistry.getTag(tag)));
            }
        }
        return categorizedTags;
    }

    void register(CategorizedTag tag) {
        List<Tag> categoryTags = tag.categoryTags();
        DefaultMutableTreeNode rootNode = getRootNode();

        DefaultMutableTreeNode currentNode = rootNode;

        for (Tag currentTag : categoryTags) {
            boolean found = false;

            for (@SuppressWarnings("unchecked")
            Enumeration<?> children = currentNode.children(); children.hasMoreElements();) {
                DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) children.nextElement();
                Tag childTag = (Tag) childNode.getUserObject();

                if (childTag.equals(currentTag)) {
                    currentNode = childNode;
                    found = true;
                    break;
                }
            }

            if (!found) {
                DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(currentTag.copy());
                addNode(currentNode, newNode);
                currentNode = newNode;
            }
        }
        save();
    }
}