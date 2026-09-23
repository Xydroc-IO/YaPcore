# How to use YaPProtect

**Jar:** `yap-protect.jar`  
Block place/break logging for grief recovery (lookup → inspect → rollback).

## Who uses it

Staff investigating grief. Players do not need commands.

## Quick lookup

```text
/yapprotect lookup user Steve
```

Staff menus / yap-staff should emit that canonical shape (see [COMMANDS.md](../../ops/COMMANDS.md)).

## Typical investigation

1. `/yapprotect lookup user <griefer>`  
2. Filter by time / world if the tool supports it  
3. Inspect the area in-game  
4. Rollback the offending changes  
5. Punish via [yapmoderation.md](yapmoderation.md)  

## Ops

- Ensure Protect is enabled on survival (where builds matter)  
- Shared DB optional depending on config — check Protect reference  
- Disk/DB growth: prune old logs on a schedule  

## Related

- Full workflows: [PROTECT.md](../../plugins/PROTECT.md)
- [yapmoderation.md](yapmoderation.md) · [yapclaims.md](yapclaims.md)
