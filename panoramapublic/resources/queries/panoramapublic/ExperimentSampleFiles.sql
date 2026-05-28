/*
 * Copyright (c) 2022-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
SELECT * FROM targetedms.SampleFile sf INNER JOIN targetedms.Replicate rep ON sf.ReplicateId = rep.Id