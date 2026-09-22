package com.yapcore.portals.cmd;

import com.yapcore.portals.PortalShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PortalSetShapeArgsTest {

    @Test
    void shapeAloneTargetsThePortalYouAreIn() {
        PortalCommandParse.SetShapeRequest req = PortalCommandParse.setShape(
                new String[] {"setshape", "arch"}, name -> false);
        assertTrue(req.ok());
        assertNull(req.name());
        assertEquals(PortalShape.Kind.ARCH, req.shape());
    }

    @Test
    void nameThenShapeOrShapeThenName() {
        PortalCommandParse.SetShapeRequest named = PortalCommandParse.setShape(
                new String[] {"setshape", "to-hub", "arch"}, name -> false);
        assertEquals("to-hub", named.name());
        assertEquals(PortalShape.Kind.ARCH, named.shape());

        PortalCommandParse.SetShapeRequest flipped = PortalCommandParse.setShape(
                new String[] {"setshape", "arch", "to-hub"}, name -> false);
        assertEquals("to-hub", flipped.name());
        assertEquals(PortalShape.Kind.ARCH, flipped.shape());
    }

    @Test
    void unknownNameIsNotTreatedAsAShape() {
        PortalCommandParse.SetShapeRequest req = PortalCommandParse.setShape(
                new String[] {"setshape", "sspawn", "arch"}, "sspawn"::equals);
        assertTrue(req.ok());
        assertEquals("sspawn", req.name());
        assertEquals(PortalShape.Kind.ARCH, req.shape());
    }

    @Test
    void missingShapeIsRejected() {
        PortalCommandParse.SetShapeRequest req = PortalCommandParse.setShape(
                new String[] {"setshape", "sspawn", "nope"}, name -> false);
        assertFalse(req.ok());
        assertEquals("shape", req.error());
    }
}
